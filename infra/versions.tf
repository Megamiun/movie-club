terraform {
  required_version = ">= 1.15.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.58"
    }
  }

  # bucket/region are deliberately not set here -- backend blocks can't reference variables at all (they're
  # resolved before the rest of the config even exists), so making the state bucket's location dynamic means
  # supplying both via partial backend configuration at `terraform init` time instead
  # (-backend-config=backend.hcl locally, or -backend-config="bucket=..."/-backend-config="region=..." in CI --
  # see infra/README.md). The state bucket's own region has nothing to do with var.aws_region (where the actual
  # infrastructure gets provisioned) -- it can live anywhere, wherever you created it. That bucket must already
  # exist before the first init either way -- a backend can't provision the bucket it depends on to store its own
  # state.
  backend "s3" {
    key          = "movie-club/terraform.tfstate"
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
