output "frontend_url" {
  value = "https://${local.frontend_domain}"
}

output "api_url" {
  value = "https://${local.api_domain}"
}

output "ec2_public_ip" {
  description = "For interactive SSH admin access."
  value       = aws_eip.app.public_ip
}

output "ec2_instance_id" {
  description = "GitHub Actions deploy target -- SSM Run Command addresses the instance by id, not IP."
  value       = aws_instance.app.id
}

output "ecr_repository_url" {
  description = "GitHub Actions pushes the backend image here."
  value       = aws_ecr_repository.backend.repository_url
}

output "s3_frontend_bucket" {
  description = "GitHub Actions syncs the Vite build output here."
  value       = aws_s3_bucket.frontend.id
}

output "s3_backups_bucket" {
  description = "Nightly pg_dump backups land here (see templates/user_data.sh.tpl's movie-club-backup.timer)."
  value       = aws_s3_bucket.backups.id
}

output "s3_photos_bucket" {
  description = "Member photo uploads land here (S3StorageClient, see s3_photos.tf)."
  value       = aws_s3_bucket.photos.id
}

output "docker_compose_content" {
  description = <<-EOT
    The exact docker-compose.yml content a fresh instance's own first boot would write (see
    templates/docker-compose.yml.tpl and user_data.sh.tpl) -- source of truth for terraform.yml's apply job,
    which pushes this same content to the *already-running* instance via SSM after every apply and restarts the
    stack. Needed because user_data only ever runs once at first boot (ec2.tf's aws_instance.app deliberately
    ignores user_data changes -- see its own comment), so a template edit alone would otherwise never reach a
    live instance without actually replacing it.
  EOT
  value       = local.docker_compose_content
}

output "cloudfront_distribution_id" {
  description = "GitHub Actions invalidates this distribution's cache after every frontend deploy."
  value       = aws_cloudfront_distribution.frontend.id
}

output "github_actions_deploy_role_arn" {
  description = "Set as the AWS_DEPLOY_ROLE_ARN repo variable (not a secret -- see .github/workflows/) for aws-actions/configure-aws-credentials to assume via OIDC."
  value       = aws_iam_role.github_actions_deploy.arn
}

output "github_actions_terraform_role_arn" {
  description = "Set as the AWS_TERRAFORM_ROLE_ARN repo variable -- used by .github/workflows/terraform.yml's plan/apply jobs. Broader permissions than github_actions_deploy_role_arn, see github_oidc_terraform.tf."
  value       = aws_iam_role.github_actions_terraform.arn
}
