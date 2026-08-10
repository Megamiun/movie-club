# Secrets live in SSM Parameter Store (SecureString, AWS-managed KMS key), not baked into EC2 user_data --
# user_data is visible indefinitely via `ec2:DescribeInstanceAttribute` and cached in plaintext on the instance's
# own disk at /var/lib/cloud/instance/user-data.txt, neither of which is an appropriate place for real secrets.
# The deploy step (GitHub Actions, over SSH) fetches these at deploy time to write the instance's `.env` file --
# see infra/README.md.
locals {
  ssm_parameter_prefix = "/movie-club"
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "${local.ssm_parameter_prefix}/jwt_secret"
  type  = "SecureString"
  value = var.jwt_secret
}

resource "aws_ssm_parameter" "database_password" {
  name  = "${local.ssm_parameter_prefix}/database_password"
  type  = "SecureString"
  value = var.database_password
}

resource "aws_ssm_parameter" "tmdb_access_token" {
  name  = "${local.ssm_parameter_prefix}/tmdb_access_token"
  type  = "SecureString"
  value = var.tmdb_access_token
}

resource "aws_ssm_parameter" "omdb_api_key" {
  name  = "${local.ssm_parameter_prefix}/omdb_api_key"
  type  = "SecureString"
  value = var.omdb_api_key
}
