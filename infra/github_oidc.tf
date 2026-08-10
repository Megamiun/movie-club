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
    # other repos that might reference this same OIDC provider.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:ref:refs/heads/main"]
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
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  name   = "movie-club-github-actions-deploy"
  role   = aws_iam_role.github_actions_deploy.id
  policy = data.aws_iam_policy_document.github_actions_deploy.json
}
