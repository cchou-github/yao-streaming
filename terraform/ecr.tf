# kind's `kind load docker-image` trick has no equivalent on real EKS -
# nodes there can't reach a laptop's local Docker daemon. This is the
# registry the production image gets pushed to instead. eks_node's IAM
# role already has AmazonEC2ContainerRegistryReadOnly attached (eks.tf),
# so no further IAM changes are needed for nodes to pull from it.
resource "aws_ecr_repository" "app" {
  name = "${var.project_name}-app"

  image_scanning_configuration {
    scan_on_push = true
  }

  # Same reasoning as aws_s3_bucket's force_destroy (see s3.tf): without
  # this, terraform destroy fails outright on a repository that still has
  # images in it. Accepted tradeoff, same as the S3 buckets - this project
  # is meant to be destroyed and rebuilt freely between sessions.
  force_delete = true

  tags = {
    Name = "${var.project_name}-app"
  }
}

# Dev iteration pushes a lot of images fast; without this they'd
# accumulate storage cost indefinitely. Keeps the most recent 10.
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep only the last 10 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
