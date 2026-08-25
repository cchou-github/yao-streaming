resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet-group"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name = "${var.project_name}-db-subnet-group"
  }
}

# Only the EKS cluster's own security group may reach 3306 - not 0.0.0.0/0,
# not the VPC CIDR broadly. A managed node group with no custom launch
# template attaches this same security group to every worker node by
# default, so this correctly means "from EKS nodes, nothing else."
resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-sg"
  description = "Allows MySQL from EKS nodes only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "MySQL from EKS nodes"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_eks_cluster.main.vpc_config[0].cluster_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-rds-sg"
  }
}

# RDS rejects "/", "@", '"', and space in the master password - the
# resource's own default special-character set includes some of those, so
# it's restricted explicitly here rather than trusting the default.
resource "random_password" "rds_master" {
  length           = 20
  override_special = "!#$%^&*()-_=+[]{}<>?"
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project_name}-mysql"
  engine         = "mysql"
  engine_version = var.rds_engine_version
  instance_class = var.rds_instance_class

  allocated_storage = var.rds_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_master_username
  password = random_password.rds_master.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  multi_az                = var.rds_multi_az
  publicly_accessible     = false
  backup_retention_period = var.rds_backup_retention_period

  deletion_protection = false
  apply_immediately   = true

  # See the variable's description for the destroy/recreate tradeoff this
  # represents. Flip to false once there's real data worth keeping.
  skip_final_snapshot       = var.rds_skip_final_snapshot
  final_snapshot_identifier = var.rds_skip_final_snapshot ? null : "${var.project_name}-mysql-final"

  # Known, accepted simplification: the app connects as this master user.
  # A separate least-privilege MySQL app account would need Terraform's
  # mysql provider to reach RDS over the network (real added complexity),
  # and Flyway needs DDL rights anyway. Worth revisiting later.

  tags = {
    Name = "${var.project_name}-mysql"
  }
}
