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
      aws_ssm_parameter.jwt_secret.arn,
      aws_ssm_parameter.database_password.arn,
      aws_ssm_parameter.tmdb_access_token.arn,
      aws_ssm_parameter.omdb_api_key.arn,
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
