# A second, separate role from github_actions_deploy (github_oidc.tf) -- that one is narrowly scoped for app
# deploys (push an image, sync a bucket, plus SSM Run Command to actually run the deploy on the instance -- real
# remote code execution on the box, wider than "push an image" sounds, but that's what the deploy mechanism
# needs). This one runs `terraform plan`/`apply` itself, which needs to create and modify IAM roles/policies
# (including, subtly, its own -- see infra/README.md's bootstrap note), security groups, DNS, ACM certs, and
# more. That's a meaningfully larger blast radius, kept in its own role rather than folded into the app-deploy
# role. The isolation this buys is one-directional, not mutual, and worth being precise about: a compromised
# github_actions_deploy can't reach this role (it has zero IAM permissions of its own -- see github_oidc.tf).
# But this role's own `iam:*` on `role/movie-club-*` (ManageOwnIamResources below) matches
# movie-club-github-actions-deploy's own name too, so a compromise *here* can modify, re-trust, or PassRole that
# other role freely. That's inherent to the design, not an oversight to fix -- this role has to be able to manage
# aws_iam_role.github_actions_deploy, since that resource is defined in this same Terraform config
# (github_oidc.tf) and touching it is a normal, intended `apply` operation. Don't read this as a mutual
# non-escalation guarantee; it only holds in the deploy-to-terraform direction.
#
# Trusted from main only, not pull_request -- this repo is public, and a `pull_request` trust condition is a
# known GitHub Actions OIDC risk for public repos: the workflow file for a `pull_request` run is sourced from the
# PR's own branch (potentially a fork), so anyone who can open a PR could rewrite the workflow to abuse a role
# trusted at that trigger. Only a push to main, or a manual workflow_dispatch targeting main (both produce the
# same ref-based `sub` claim below), can ever assume this role -- in practice that means only the repo owner, who
# has merge rights and can trigger a dispatch against main. PRs still get *some* automated feedback --
# .github/workflows/terraform.yml's `validate` job runs `terraform fmt`/`validate` without any AWS credentials at
# all, so it needs no trust here.
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

    # Two acceptable values, not one: `plan`/`apply` both set `environment: production` (the approval gate), which
    # gives them a *different* `sub` claim entirely -- <prefix>:environment:production instead of the usual
    # ref-based one, not an addition to it. `validate` has no environment, so it still presents the ref-based
    # subject. local.github_oidc_subject_prefix embeds the repo's immutable owner/repo ids, not just their
    # (mutable) names -- see variables.tf's github_owner_id/github_repo_id comment for why that's mandatory, not
    # optional, for this repo.
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

resource "aws_iam_role" "github_actions_terraform" {
  name               = "movie-club-github-actions-terraform"
  assume_role_policy = data.aws_iam_policy_document.github_actions_terraform_assume_role.json
}

# Broad per-service permissions (action wildcards), not a line-by-line enumeration of every API call Terraform's
# AWS provider happens to make -- that list is long, changes across provider versions, and a single missing action
# just produces a confusing mid-apply failure rather than a real security improvement. Resources are scoped by
# this project's own naming convention/known ARNs everywhere that's actually possible (IAM roles/instance
# profiles, the three S3 buckets, the ECR repo, the SSM parameter prefix, this project's own Route53 hosted zone
# -- see ManageOwnHostedZoneRecords below); CloudFront/ACM/EC2 resources don't support that kind of pre-creation
# name-based scoping in IAM (or, for EC2, the one-time VPC/subnet/security-group bootstrap actions don't, and
# scoping only the rest wasn't judged worth the added complexity here), so those stay resource "*" within their
# own service. The real safety control here is the required-reviewer GitHub Environment gate on the apply job
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
      aws_s3_bucket.backups.arn, "${aws_s3_bucket.backups.arn}/*",
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

  # Unlike CloudFront/ACM/EC2 above, Route53 record management genuinely does support resource-level scoping --
  # tightened to just this project's own hosted zone (dns.tf/cloudfront.tf's aws_route53_record resources all
  # reference the same data.aws_route53_zone.apex), not every zone on the account. Two actions still can't be
  # scoped that way, each split into its own statement with resources = ["*"] and a comment saying why, matching
  # the pattern the S3/ACM/CloudFront statements already use for their own unscopable bootstrap actions.
  statement {
    sid       = "FindOwnHostedZone"
    actions   = ["route53:ListHostedZones", "route53:ListHostedZonesByName"]
    resources = ["*"] # finding a zone by domain name needs an account-wide list -- the zone ARN this scopes down
    # to below doesn't exist as a known value until this lookup resolves it
  }

  statement {
    sid       = "ManageOwnHostedZoneRecords"
    actions   = ["route53:GetHostedZone", "route53:ListResourceRecordSets", "route53:ChangeResourceRecordSets"]
    resources = ["arn:aws:route53:::hostedzone/${data.aws_route53_zone.apex.zone_id}"]
  }

  statement {
    sid       = "ReadRoute53ChangeStatus"
    actions   = ["route53:GetChange"]
    resources = ["*"] # a change/<id> ARN doesn't exist until *after* ChangeResourceRecordSets creates it -- same
    # bootstrap problem as s3:CreateBucket elsewhere in this file, no narrower scoping possible
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

  # A different, unrelated need for KMS than the one removed above: iam.tf's own `data "aws_kms_alias" "ssm"`
  # (used to build the *EC2 role's* policy, not this one) has to be resolved at plan time regardless of who's
  # running Terraform -- the aws_kms_alias data source calls kms:ListAliases (find the alias by name, no narrower
  # "get one alias" API exists) and kms:DescribeKey (resolve the alias's target key details). Doesn't grant
  # Decrypt -- this role never reads a secret's actual value, only the key's ARN, to write it into someone else's
  # policy document.
  statement {
    sid       = "ResolveSsmKmsAlias"
    actions   = ["kms:ListAliases", "kms:DescribeKey"]
    resources = ["*"] # ListAliases doesn't support resource-level scoping at all; DescribeKey does, but scoping
    # it to a specific key ARN isn't possible here since that ARN is exactly what this lookup exists to resolve
  }
}

resource "aws_iam_role_policy" "github_actions_terraform" {
  name   = "movie-club-github-actions-terraform"
  role   = aws_iam_role.github_actions_terraform.id
  policy = data.aws_iam_policy_document.github_actions_terraform.json
}
