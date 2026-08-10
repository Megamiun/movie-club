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
  description = "Root EBS volume size -- holds the Postgres data volume via docker-compose. Persists across instance stop/start and reboot, but is destroyed if the instance itself is ever replaced/terminated (e.g. `terraform destroy`, or changing an argument that forces replacement) -- acceptable for a small club's data given the tradeoff against a second attached volume's added complexity, but worth knowing before you `destroy`."
  type        = number
  default     = 30
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

