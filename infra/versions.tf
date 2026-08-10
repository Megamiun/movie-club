terraform {
  required_version = ">= 1.10.0" # native S3 state locking (use_lockfile) needs 1.10+, no DynamoDB table required

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # The bucket below must already exist before `terraform init` -- see infra/README.md's bootstrap step. Can't be
  # created by this same configuration (a backend can't provision the bucket it depends on to store its own state).
  backend "s3" {
    bucket       = "movie-club-terraform-state"
    key          = "movie-club/terraform.tfstate"
    region       = "us-east-1"
    use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region
}

# CloudFront's ACM cert must live in us-east-1 no matter which region aws_region is -- kept as an explicit alias
# rather than assuming aws_region already is us-east-1, so this stays correct even if that default changes.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}
