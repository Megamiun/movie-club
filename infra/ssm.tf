# Secrets live in SSM Parameter Store (SecureString, AWS-managed KMS key), not baked into EC2 user_data --
# user_data is visible indefinitely via `ec2:DescribeInstanceAttribute` and cached in plaintext on the instance's
# own disk at /var/lib/cloud/instance/user-data.txt, neither of which is an appropriate place for real secrets.
# The deploy step (GitHub Actions, over SSM Run Command) fetches these at deploy time to write the instance's
# `.env` file -- see infra/README.md.
#
# These are `data` sources, not `resource`s -- Terraform never creates or holds the plaintext value. Only the ARN
# is actually used (iam.tf, to scope the EC2 role's read policy), so `with_decryption = false` on every one of
# these -- without it, the data source would still fetch and cache the *decrypted* value into Terraform state by
# default (state stores a data source's full result just like a resource's, "sensitive" or not; that's a common
# Terraform gotcha and would have quietly defeated the reason for using `data` here in the first place). Each
# parameter must already exist before `terraform plan`/`apply` -- see infra/README.md's bootstrap step for the
# one-time `aws ssm put-parameter` commands to create them by hand.
locals {
  ssm_parameter_prefix = "/movie-club"
}

data "aws_ssm_parameter" "jwt_secret" {
  name            = "${local.ssm_parameter_prefix}/jwt_secret"
  with_decryption = false
}

data "aws_ssm_parameter" "database_password" {
  name            = "${local.ssm_parameter_prefix}/database_password"
  with_decryption = false
}

data "aws_ssm_parameter" "tmdb_access_token" {
  name            = "${local.ssm_parameter_prefix}/tmdb_access_token"
  with_decryption = false
}

data "aws_ssm_parameter" "omdb_api_key" {
  name            = "${local.ssm_parameter_prefix}/omdb_api_key"
  with_decryption = false
}
