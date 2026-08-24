data "aws_caller_identity" "current" {}

# --- Cluster IAM role ---

data "aws_iam_policy_document" "eks_cluster_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["eks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "eks_cluster" {
  name               = "${var.project_name}-eks-cluster-role"
  assume_role_policy = data.aws_iam_policy_document.eks_cluster_assume_role.json
}

resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  role       = aws_iam_role.eks_cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

# --- Cluster ---

resource "aws_eks_cluster" "main" {
  name     = local.cluster_name
  role_arn = aws_iam_role.eks_cluster.arn
  version  = var.eks_cluster_version

  # Not "API" alone: AWS's own recommended migration path is CONFIG_MAP ->
  # API_AND_CONFIG_MAP -> API, so both the legacy aws-auth ConfigMap and
  # the newer Access Entry API (aws_eks_access_entry, below) keep working.
  # Required for aws_eks_access_entry.root - EKS defaults new clusters to
  # CONFIG_MAP-only, which doesn't support Access Entries at all.
  access_config {
    authentication_mode = "API_AND_CONFIG_MAP"
    # Already true on the live cluster (it's a one-time, creation-only
    # setting) - must be restated explicitly, or Terraform reads the
    # omission as "unset" and force-replaces the entire cluster to
    # satisfy a change to a field that can't actually be changed later.
    bootstrap_cluster_creator_admin_permissions = true
  }

  vpc_config {
    subnet_ids = concat(aws_subnet.public[*].id, aws_subnet.private[*].id)

    # No bastion/VPN in scope, so kubectl needs to reach the API directly.
    # IAM still gates actual calls; narrow public_access_cidrs to your own
    # IP once known (see the variable's description).
    endpoint_public_access  = true
    endpoint_private_access = true
    public_access_cidrs     = var.eks_cluster_endpoint_public_access_cidrs
  }

  depends_on = [aws_iam_role_policy_attachment.eks_cluster_policy]
}

# --- Node IAM role ---

data "aws_iam_policy_document" "eks_node_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "eks_node" {
  name               = "${var.project_name}-eks-node-role"
  assume_role_policy = data.aws_iam_policy_document.eks_node_assume_role.json
}

resource "aws_iam_role_policy_attachment" "eks_node_worker" {
  role       = aws_iam_role.eks_node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

resource "aws_iam_role_policy_attachment" "eks_node_cni" {
  role       = aws_iam_role.eks_node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

resource "aws_iam_role_policy_attachment" "eks_node_ecr_readonly" {
  role       = aws_iam_role.eks_node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# Optional, no extra AWS cost: lets you `aws ssm start-session` onto a node
# for debugging without managing SSH keys.
resource "aws_iam_role_policy_attachment" "eks_node_ssm" {
  role       = aws_iam_role.eks_node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# --- Node group ---

resource "aws_eks_node_group" "main" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.project_name}-node-group"
  node_role_arn   = aws_iam_role.eks_node.arn
  subnet_ids      = aws_subnet.private[*].id

  instance_types = var.eks_node_instance_types
  disk_size      = var.eks_node_disk_size

  scaling_config {
    desired_size = var.eks_node_desired_size
    min_size     = var.eks_node_min_size
    max_size     = var.eks_node_max_size
  }

  # Dodges a known IAM-propagation race: node group creation can fail
  # intermittently on first apply if it starts before these attachments
  # have propagated.
  depends_on = [
    aws_iam_role_policy_attachment.eks_node_worker,
    aws_iam_role_policy_attachment.eks_node_cni,
    aws_iam_role_policy_attachment.eks_node_ecr_readonly,
  ]
}

# --- OIDC provider, for IRSA (see iam.tf) ---

data "tls_certificate" "eks_oidc" {
  url = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  url             = aws_eks_cluster.main.identity[0].oidc[0].issuer
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks_oidc.certificates[0].sha1_fingerprint]
}

# By default, only the IAM principal that ran `terraform apply` (the
# yao-streaming-terraform IAM user) gets Kubernetes-level access to the
# cluster - that's a separate authorization layer from plain IAM, and it's
# why the AWS Console's EKS "Resources" tab (which proxies into the
# Kubernetes API) shows nothing for the root account even though root can
# see the cluster's basic metadata fine. This grants root that same
# cluster-admin access explicitly.
resource "aws_eks_access_entry" "root" {
  cluster_name  = aws_eks_cluster.main.name
  principal_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
}

resource "aws_eks_access_policy_association" "root_admin" {
  cluster_name  = aws_eks_cluster.main.name
  principal_arn = aws_eks_access_entry.root.principal_arn
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }
}
