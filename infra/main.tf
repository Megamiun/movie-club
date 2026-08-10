data "aws_route53_zone" "apex" {
  name         = var.domain_name
  private_zone = false
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
  # Frontend is served from the apex domain itself (no subdomain) -- only the backend gets one (api.<domain>).
  frontend_domain = var.domain_name
  api_domain      = "${var.api_subdomain}.${var.domain_name}"

  # GitHub's immutable OIDC subject format (see variables.tf's github_owner_id/github_repo_id comment) -- shared
  # by both github_oidc.tf and github_oidc_terraform.tf's trust policies, since both need the same repo segment.
  github_oidc_subject_prefix = "repo:${split("/", var.github_repository)[0]}@${var.github_owner_id}/${split("/", var.github_repository)[1]}@${var.github_repo_id}"
}
