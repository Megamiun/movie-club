variable "aws_region" {
  description = "AWS region for every resource except the CloudFront ACM cert, which must be us-east-1 regardless."
  type        = string
  default     = "us-east-1"
}

variable "domain_name" {
  description = "Root domain already managed by an existing Route53 hosted zone (e.g. \"example.com\"). Not created by this configuration -- looked up via data source."
  type        = string
}

variable "frontend_subdomain" {
  description = "Subdomain the frontend (S3 + CloudFront) is served from, e.g. \"app\" for app.example.com."
  type        = string
  default     = "app"
}

variable "api_subdomain" {
  description = "Subdomain the backend (EC2, behind Caddy) is served from, e.g. \"api\" for api.example.com."
  type        = string
  default     = "api"
}

variable "cloudfront_price_class" {
  description = "CloudFront price class -- PriceClass_100 (US/Canada/Europe edge locations only) is the cheapest tier and plenty for a small friend group; widen it if the club is elsewhere."
  type        = string
  default     = "PriceClass_100"
}

variable "instance_type" {
  description = "EC2 instance type running the db+backend containers. t4g.small (arm64/Graviton) is cheap and plenty for a small club -- the app doesn't build on-box, it only pulls prebuilt images, so 2GB RAM is enough."
  type        = string
  default     = "t4g.small"
}

variable "root_volume_size_gb" {
  description = "Root EBS volume size -- OS + Docker images only now (Postgres' own data lives on the separate volume below, not here). ~3-4GB OS+Docker, ~1.5GB images, under 1GB logs -- 10GB gives comfortable headroom over that without paying gp3's $0.08/GB-month for space this volume will never actually use. Destroyed if the instance itself is ever replaced/terminated (e.g. `terraform destroy`), same as any root volume -- fine here since nothing on it is unique data, unlike data_volume_size_gb below."
  type        = number
  default     = 10
}

variable "data_volume_size_gb" {
  description = "Size of the separate EBS volume holding just Postgres' own data (see ec2.tf's aws_ebs_volume.postgres_data and templates/user_data.sh.tpl's mount logic) -- kept apart from the root volume specifically so the data survives independently of the instance itself (a root-volume replacement, or swapping instance types, no longer risks it). 10GB is generous for a small club's actual data volume (genuinely tiny -- a few hundred/thousand rows across all tables), resizable later via `aws ec2 modify-volume` without needing to recreate anything."
  type        = number
  default     = 10
}

variable "ssh_public_key" {
  description = "SSH public key (e.g. contents of ~/.ssh/id_ed25519.pub) for interactive admin access only -- GitHub Actions deploys via SSM Run Command instead (see github_oidc.tf), not SSH, since a GitHub-hosted runner's IP is dynamic and could never satisfy ssh_allowed_cidr below."
  type        = string
}

variable "ssh_allowed_cidr" {
  description = "CIDR block allowed to SSH into the instance (port 22) for interactive admin access -- e.g. \"1.2.3.4/32\" for your own IP, or a narrower range. No default: an open SSH port to the internet is not a safe thing to default to. GitHub Actions never needs this -- see ssh_public_key."
  type        = string
}

variable "tmdb_access_token" {
  description = "TMDB API read access token -- see backend's TmdbClient. Sensitive; pass via a .tfvars file that's gitignored, or TF_VAR_tmdb_access_token."
  type        = string
  sensitive   = true
}

variable "omdb_api_key" {
  description = "OMDb API key (optional -- OmdbClient no-ops without one, see CLAUDE.md). Sensitive."
  type        = string
  sensitive   = true
  default     = ""
}

variable "jwt_secret" {
  description = "Secret used to sign auth JWTs -- must be a real random value in production, not the dev default."
  type        = string
  sensitive   = true
}

variable "database_password" {
  description = "Password for the Postgres superuser the backend connects as."
  type        = string
  sensitive   = true
}

variable "github_repository" {
  description = "GitHub repo GitHub Actions deploys from, as \"owner/repo\" -- scopes the OIDC trust policy so only workflow runs from this exact repo (on main) can assume the deploy role."
  type        = string
}

