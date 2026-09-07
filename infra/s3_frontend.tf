# Private bucket -- never served directly, only ever read by CloudFront via Origin Access Control (below). The
# Vite build output (frontend/dist) is synced here by GitHub Actions on every deploy.
resource "aws_s3_bucket" "frontend" {
  bucket = "${var.project_name}-frontend"

  # Without this, Terraform refuses to delete the bucket while it still holds objects (S3's own BucketNotEmpty
  # error) -- confirmed live the first time this bucket needed replacing (the project_name rename). Safe to
  # auto-empty on destroy specifically because everything in it is regenerable build output, re-synced by
  # deploy-frontend.yml on the next deploy -- never treat this as a template for a bucket holding real data.
  force_destroy = true
}

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

data "aws_iam_policy_document" "frontend_bucket" {
  statement {
    sid       = "AllowCloudFrontReadViaOAC"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.frontend.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.frontend.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = data.aws_iam_policy_document.frontend_bucket.json
}
