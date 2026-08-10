# A second, separate role from github_actions_deploy (github_oidc.tf) -- that one is narrowly scoped for app
# deploys (push an image, sync a bucket). This one runs `terraform plan`/`apply` itself, which needs to create and
# modify IAM roles/policies (including, subtly, its own -- see infra/README.md's bootstrap note), security groups,
# DNS, ACM certs, and more. That's a meaningfully larger blast radius, kept in its own role rather than folded into
# the app-deploy role, so a compromise of one doesn't automatically hand over the other.
#
# Trusted from push to main ONLY, not pull_request -- this repo is public, and a `pull_request` trust condition is
# a known GitHub Actions OIDC risk for public repos: the workflow file for a `pull_request` run is sourced from the
# PR's own branch (potentially a fork), so anyone who can open a PR could rewrite the workflow to abuse a role
# trusted at that trigger. Only a push to main (which here means: only the repo owner, who has merge rights) can
# ever assume this role. PRs still get *some* automated feedback -- .github/workflows/terraform.yml's `validate`
# job runs `terraform fmt`/`validate` without any AWS credentials at all, so it needs no trust here.
data "aws_iam_policy_document" "github_actions_terraform_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "github_actions_terraform" {
  name               = "movie-club-github-actions-terraform"
  assume_role_policy = data.aws_iam_policy_document.github_actions_terraform_assume_role.json
}

# Broad per-service permissions (action wildcards), not a line-by-line enumeration of every API call Terraform's
# AWS provider happens to make -- that list is long, changes across provider versions, and a single missing action
# just produces a confusing mid-apply failure rather than a real security improvement. Resources are scoped by
# this project's own naming convention/known ARNs everywhere that's actually possible (IAM roles/instance
# profiles, the two S3 buckets, the ECR repo, the SSM parameter prefix); CloudFront/ACM/Route53/EC2 resources
# don't support that kind of pre-creation name-based scoping in IAM, so those stay resource "*" within their own
# service. The real safety control here is the required-reviewer GitHub Environment gate on the apply job
# (.github/workflows/terraform.yml), not fine-grained IAM alone -- there's no way to scope "create IAM roles with
# arbitrary permissions" down much further without a permissions boundary, which is more machinery than a
# single-app hobby account needs.
data "aws_iam_policy_document" "github_actions_terraform" {
  statement {
    sid     = "ManageOwnIamResources"
    actions = ["iam:*"]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/movie-club-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:instance-profile/movie-club-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/token.actions.githubusercontent.com",
    ]
  }

  statement {
    sid       = "ManageEc2Resources"
    actions   = ["ec2:*"]
    resources = ["*"]
  }

  statement {
    sid     = "ManageOwnS3Buckets"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.frontend.arn, "${aws_s3_bucket.frontend.arn}/*",
      "arn:aws:s3:::${var.tf_state_bucket_name}", "arn:aws:s3:::${var.tf_state_bucket_name}/*",
    ]
  }

  statement {
    sid       = "ManageCloudFront"
    actions   = ["cloudfront:*"]
    resources = ["*"]
  }

  statement {
    sid       = "ManageAcm"
    actions   = ["acm:*"]
    resources = ["*"]
  }

  statement {
    sid       = "ManageRoute53"
    actions   = ["route53:*"]
    resources = ["*"]
  }

  statement {
    sid       = "ManageOwnEcrRepository"
    actions   = ["ecr:*"]
    resources = [aws_ecr_repository.backend.arn]
  }

  # Read-only, and only GetParameter -- ssm.tf's four secrets are `data` sources, not `resource`s (Terraform never
  # creates/deletes/modifies an SSM parameter in this config, see ssm.tf's own comment), and with_decryption =
  # false means it doesn't even need KMS access to read them. GetParameter is the only API a data source of this
  # kind calls (not the plural GetParameters -- each block fetches one exact name, not a batch); no Describe*
  # needed either. Deliberately excludes ssm:DeleteParameter and friends -- there's no legitimate reason for this
  # role to ever be able to delete these.
  statement {
    sid       = "ReadOwnSsmParameters"
    actions   = ["ssm:GetParameter"]
    resources = ["arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter${local.ssm_parameter_prefix}/*"]
  }
}

resource "aws_iam_role_policy" "github_actions_terraform" {
  name   = "movie-club-github-actions-terraform"
  role   = aws_iam_role.github_actions_terraform.id
  policy = data.aws_iam_policy_document.github_actions_terraform.json
}
