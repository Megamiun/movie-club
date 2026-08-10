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
