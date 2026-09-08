# Member photo uploads (`MemberService.uploadPhoto`, `S3StorageClient`) -- unlike backups above, this bucket is
# deliberately public-read: a photo needs to load directly in an `<img src>` from the browser, with no signing/
# proxy in front of it. The public-read policy is set here, by Terraform, rather than left to `S3StorageClient`'s
# own lazy first-use bucket-creation path (which still exists for local dev against MinIO) -- a real AWS account's
# modern "Block Public Access" default (on unless explicitly overridden, see the public access block below) would
# reject that policy call at runtime, and the app has no business holding `s3:CreateBucket`/`s3:PutBucketPolicy`
# permissions in production anyway (see iam.tf's own, much narrower grant).
resource "aws_s3_bucket" "photos" {
  bucket = "${var.project_name}-photos"

  # Same ordering guard as s3_backups.tf's own -- see that file's comment for the exact bootstrap deadlock this
  # avoids (CreateBucket racing the terraform role's own policy update that grants it).
  depends_on = [aws_iam_role_policy.github_actions_terraform]
}

resource "aws_s3_bucket_public_access_block" "photos" {
  bucket = aws_s3_bucket.photos.id
  # The one bucket in this project that's meant to be public -- every flag here stays false, unlike
  # s3_backups.tf/s3_frontend.tf (frontend is fronted by CloudFront instead of direct public bucket access).
  block_public_acls       = false
  block_public_policy     = false
  ignore_public_acls      = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_ownership_controls" "photos" {
  bucket = aws_s3_bucket.photos.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "photos" {
  bucket = aws_s3_bucket.photos.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Public GetObject only -- no List, no write. Matches `S3StorageClient.publicReadPolicy`'s own shape exactly (the
# same policy the app would otherwise try to set itself against a freshly-created bucket).
data "aws_iam_policy_document" "photos_public_read" {
  statement {
    sid       = "PublicReadOnly"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.photos.arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
  }
}

resource "aws_s3_bucket_policy" "photos" {
  bucket = aws_s3_bucket.photos.id
  policy = data.aws_iam_policy_document.photos_public_read.json
  # The bucket policy update itself needs the public access block already lifted -- otherwise this call is
  # rejected the exact same way `S3StorageClient`'s own lazy attempt would be (see this file's top comment).
  depends_on = [aws_s3_bucket_public_access_block.photos]
}
