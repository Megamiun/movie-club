data "aws_route53_zone" "this" {
  name         = var.domain_name
  private_zone = false
}

# Single EC2 instance in the account's default VPC/subnet -- this app doesn't need a dedicated VPC, and standing
# one up (NAT gateway, route tables, etc.) would be pure added cost/complexity for one box.
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# Resolved once here (not read off aws_instance.app directly) so the postgres data volume's own availability_zone
# (ec2.tf) can depend on the same subnet the instance uses instead of on the instance itself -- avoids a circular
# dependency, since the instance's own user_data needs the volume's id already baked in before the instance exists.
data "aws_subnet" "selected" {
  id = data.aws_subnets.default.ids[0]
}

# Arm64 (Graviton) Amazon Linux 2023 -- matches instance_type's default t4g family. Both Dockerfiles' base images
# (eclipse-temurin, gradle, postgres) publish multi-arch manifests, so arm64 needs no Dockerfile changes.
data "aws_ami" "al2023_arm64" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-arm64"]
  }

  filter {
    name   = "architecture"
    values = ["arm64"]
  }
}

locals {
  frontend_domain = "${var.frontend_subdomain}.${var.domain_name}"
  api_domain      = "${var.api_subdomain}.${var.domain_name}"
}
