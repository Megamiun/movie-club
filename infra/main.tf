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
  # Grafana (self-hosted alongside Prometheus on the same EC2 instance -- see docker-compose.yml.tpl) gets its
  # own subdomain too, same reverse-proxy-behind-Caddy shape as api_domain. Prometheus itself is never exposed
  # this way -- only reachable from Grafana over the compose-internal network.
  metrics_domain = "${var.metrics_subdomain}.${var.domain_name}"

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
    metrics_domain = local.metrics_domain
  })

  # Same single-source-of-truth reasoning as docker_compose_content above, and for the same operational reason:
  # ec2.tf's aws_instance.app ignores user_data changes (see its own comment), so these need to be pushed to an
  # already-running instance the same way (outputs.tf -- terraform.yml's apply job), not just baked into a fresh
  # instance's first boot.
  caddyfile_content = templatefile("${path.module}/templates/Caddyfile.tpl", {
    api_domain     = local.api_domain
    metrics_domain = local.metrics_domain
  })

  # Static (no Terraform variables of their own yet), but still rendered as locals/outputs rather than spliced
  # into user_data.sh.tpl as a literal heredoc -- same live-sync reasoning as caddyfile_content, and it costs
  # nothing extra now to keep every instance-boot config file on the one mechanism that actually reaches a live
  # instance, rather than some being live-syncable and others silently only taking effect on a future replacement.
  prometheus_config_content   = file("${path.module}/templates/prometheus.yml")
  grafana_datasources_content = file("${path.module}/templates/grafana-datasources.yml")
}
