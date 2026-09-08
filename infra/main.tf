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

  # Rendered once, referenced by both ec2.tf's user_data (a fresh instance's first-boot copy) and the
  # docker_compose_content output (outputs.tf -- terraform.yml's apply job pushes this same content to an
  # *already-running* instance via SSM, since user_data itself never re-runs there). Single source of truth so
  # the two paths can never drift from each other by construction.
  docker_compose_content = templatefile("${path.module}/templates/docker-compose.yml.tpl", {
    aws_region     = var.aws_region
    log_group      = aws_cloudwatch_log_group.backend.name
    s3_bucket_name = aws_s3_bucket.photos.id
  })
}
