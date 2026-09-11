data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2" {
  name               = "movie-club-ec2"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

# Lets the instance `docker pull` from the backend's own ECR repo -- nothing broader than that.
resource "aws_iam_role_policy_attachment" "ecr_read" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# AWS-managed key backing every SecureString parameter below (see ssm.tf) -- resolved via data source because KMS
# does not accept an alias ARN/name as the Resource for Decrypt/GenerateDataKey in an IAM identity policy (only
# for alias-management calls like CreateAlias/DescribeKey); the actual key ARN behind the alias is required here.
data "aws_kms_alias" "ssm" {
  name = "alias/aws/ssm"
}

data "aws_iam_policy_document" "ssm_read_secrets" {
  statement {
    actions = ["ssm:GetParameter", "ssm:GetParameters"]
    resources = [
      data.aws_ssm_parameter.jwt_secret.arn,
      data.aws_ssm_parameter.database_password.arn,
      data.aws_ssm_parameter.tmdb_access_token.arn,
      data.aws_ssm_parameter.omdb_api_key.arn,
      data.aws_ssm_parameter.grafana_admin_password.arn,
    ]
  }

  # SecureString parameters using the AWS-managed `aws/ssm` key still need the calling principal to be granted
  # kms:Decrypt explicitly in its own identity-based policy -- the key's resource policy alone isn't always
  # sufficient in practice, so this is the belt-and-suspenders half of that pair.
  statement {
    actions   = ["kms:Decrypt"]
    resources = [data.aws_kms_alias.ssm.target_key_arn]
  }
}

resource "aws_iam_role_policy" "ssm_read_secrets" {
  name   = "movie-club-ssm-read-secrets"
  role   = aws_iam_role.ec2.id
  policy = data.aws_iam_policy_document.ssm_read_secrets.json
}

# Registers the instance with SSM so GitHub Actions can deploy via `aws ssm send-command` instead of SSH (see
# github_oidc.tf) -- SSH stays available for interactive admin access (security.tf's ssh_allowed_cidr), but CI
# never needs port 22 open to it, which a GitHub-hosted runner's dynamic IP couldn't satisfy anyway.
resource "aws_iam_role_policy_attachment" "ssm_managed_instance" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ec2" {
  name = "movie-club-ec2"
  role = aws_iam_role.ec2.name
}

# Lets the instance's own nightly backup timer (templates/user_data.sh.tpl) upload to s3_backups.tf's bucket --
# scoped to just that one bucket, not a broader S3 policy. No read/delete permissions: the instance only ever
# needs to write new backups, never list/restore/delete existing ones (that's a human's job, via the console/CLI
# with their own credentials).
data "aws_iam_policy_document" "s3_write_backups" {
  statement {
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.backups.arn}/*"]
  }
}

resource "aws_iam_role_policy" "s3_write_backups" {
  name   = "movie-club-s3-write-backups"
  role   = aws_iam_role.ec2.id
  policy = data.aws_iam_policy_document.s3_write_backups.json
}

# Lets the backend container itself (via the instance's own role -- DefaultCredentialsProvider, no keys configured,
# see S3StorageClient's own comment) upload member photos to s3_photos.tf's bucket. `s3:ListBucket` on the bucket
# itself (not `/*`) is the one addition beyond a plain write grant like s3_write_backups above -- `S3StorageClient.
# ensureBucketExists` calls `HeadBucket` before every upload (to skip its own create-bucket/set-policy fallback,
# which s3_photos.tf already handles up front in production), and `HeadBucket` is authorized by `s3:ListBucket`,
# not `s3:GetObject`/`s3:PutObject`. Without this, that call gets a 403 (not the 404 `ensureBucketExists` treats
# as "doesn't exist yet, create it"), which its `if (statusCode != 404) throw` rethrows -- every upload would fail
# even though the bucket genuinely exists and PutObject alone would otherwise have worked. Still no read/delete on
# the objects themselves: a browser reads an uploaded photo directly via the bucket's own public-read policy, the
# app never reads its own uploads back.
data "aws_iam_policy_document" "s3_write_photos" {
  statement {
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.photos.arn}/*"]
  }
  statement {
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.photos.arn]
  }
}

resource "aws_iam_role_policy" "s3_write_photos" {
  name   = "movie-club-s3-write-photos"
  role   = aws_iam_role.ec2.id
  policy = data.aws_iam_policy_document.s3_write_photos.json
}

# Lets the instance's own backend container ship its logs to CloudWatch Logs (cloudwatch.tf) via the `awslogs`
# docker logging driver -- scoped to just that one log group, and no logs:CreateLogGroup: the group already exists
# (Terraform-managed), and the production compose file (templates/user_data.sh.tpl) sets `awslogs-create-group:
# "false"` so the driver never needs that permission in the first place.
data "aws_iam_policy_document" "cloudwatch_write_backend_logs" {
  statement {
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.backend.arn}:*"]
  }
}

resource "aws_iam_role_policy" "cloudwatch_write_backend_logs" {
  name   = "movie-club-cloudwatch-write-backend-logs"
  role   = aws_iam_role.ec2.id
  policy = data.aws_iam_policy_document.cloudwatch_write_backend_logs.json
}
