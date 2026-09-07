# Nightly pg_dump backups, uploaded by a systemd timer on the EC2 instance itself (templates/user_data.sh.tpl) --
# see ec2.tf's aws_ebs_volume.postgres_data comment for why the primary copy (that EBS volume) alone isn't enough:
# it survives an instance replacement, but not e.g. a mistaken `docker volume rm`, a bad migration, or a
# terraform destroy with the lifecycle guard removed by hand. Private bucket, never served -- nothing here is
# meant to be read outside the EC2 instance's own IAM role (iam.tf) and whoever has direct AWS console/CLI access.
resource "aws_s3_bucket" "backups" {
  bucket = "${var.project_name}-backups"
}

resource "aws_s3_bucket_public_access_block" "backups" {
  bucket                  = aws_s3_bucket.backups.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "backups" {
  bucket = aws_s3_bucket.backups.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Versioning + a short noncurrent-version expiry, not just plain overwrite-in-place -- a backup that uploads
# successfully but is itself corrupt (e.g. pg_dump exiting midway, still exit 0) would otherwise silently replace
# the last good copy under the same day's key with nothing to recover from.
resource "aws_s3_bucket_versioning" "backups" {
  bucket = aws_s3_bucket.backups.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id
  rule {
    id     = "expire-old-backups"
    status = "Enabled"
    filter {}

    expiration {
      days = var.backup_retention_days
    }

    noncurrent_version_expiration {
      noncurrent_days = var.backup_retention_days
    }
  }
}
