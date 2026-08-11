# Lets GitHub Actions assume an AWS role via OIDC federation instead of storing long-lived AWS access keys as a
# GitHub secret -- the workflow exchanges a short-lived GitHub-issued token for temporary AWS credentials at
# runtime, scoped by the trust policy below to exactly this repo's main branch.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  # GitHub's OIDC provider root CA thumbprint -- documented by GitHub/Hashicorp's own examples. AWS validates the
  # certificate chain itself for GitHub's provider regardless, but the resource still requires a value here.
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

data "aws_iam_policy_document" "github_actions_assume_role" {
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

    # Only workflow runs triggered from a push to main on this exact repo -- not PRs, not other branches, not
    # other repos that might reference this same OIDC provider. Two acceptable values, not one: a job that sets
    # `environment: production` (deploy-backend.yml/deploy-frontend.yml's `deploy` jobs) gets a *different* `sub`
    # claim from GitHub entirely -- <prefix>:environment:production instead of the usual ref-based one, not an
    # addition to it -- so the ref-only condition alone would always reject exactly the jobs this role exists for.
    # local.github_oidc_subject_prefix embeds the repo's immutable owner/repo ids, not just their (mutable) names
    # -- see variables.tf's github_owner_id/github_repo_id comment for why that's mandatory, not optional, for
    # this repo.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "${local.github_oidc_subject_prefix}:ref:refs/heads/main",
        "${local.github_oidc_subject_prefix}:environment:production",
      ]
    }
  }
}

resource "aws_iam_role" "github_actions_deploy" {
  name               = "movie-club-github-actions-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role.json
}

data "aws_iam_policy_document" "github_actions_deploy" {
  statement {
    sid       = "PushBackendImage"
    actions   = ["ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage", "ecr:BatchCheckLayerAvailability", "ecr:PutImage", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart", "ecr:CompleteLayerUpload"]
    resources = [aws_ecr_repository.backend.arn]
  }

  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"] # this action doesn't support resource-level scoping -- AWS requires "*" here
  }

  statement {
    sid       = "SyncFrontendBucket"
    actions   = ["s3:PutObject", "s3:DeleteObject", "s3:ListBucket"]
    resources = [aws_s3_bucket.frontend.arn, "${aws_s3_bucket.frontend.arn}/*"]
  }

  statement {
    sid       = "InvalidateFrontendDistribution"
    actions   = ["cloudfront:CreateInvalidation"]
    resources = [aws_cloudfront_distribution.frontend.arn]
  }

  # Deploys run over SSM Run Command, not SSH -- a GitHub-hosted runner's IP is dynamic per run, so it could never
  # satisfy security.tf's ssh_allowed_cidr (that's deliberately scoped to the operator's own fixed IP for
  # interactive admin access only). SSM instead authenticates through this same IAM role.
  statement {
    sid       = "DeployViaSsmRunCommand"
    actions   = ["ssm:SendCommand"]
    resources = [aws_instance.app.arn, "arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript"]
  }

  statement {
    sid       = "ReadDeployCommandResult"
    actions   = ["ssm:GetCommandInvocation"]
    resources = ["*"] # this action doesn't support resource-level scoping -- AWS requires "*" here
  }

  # Lets deploy-backend.yml resolve the instance id by its Name tag at deploy time instead of storing it in a repo
  # variable that would go stale the moment the instance is ever replaced (unlike DeployViaSsmRunCommand above,
  # which references aws_instance.app.arn directly and so never goes stale on its own).
  statement {
    sid       = "ResolveInstanceIdByTag"
    actions   = ["ec2:DescribeInstances"]
    resources = ["*"] # this action doesn't support resource-level scoping -- AWS requires "*" here
  }
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  name   = "movie-club-github-actions-deploy"
  role   = aws_iam_role.github_actions_deploy.id
  policy = data.aws_iam_policy_document.github_actions_deploy.json
}
