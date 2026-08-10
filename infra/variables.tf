variable "aws_region" {
  description = "AWS region for every resource except the CloudFront ACM cert, which must be us-east-1 regardless."
  type        = string
  default     = "us-east-1"
}

variable "domain_name" {
  description = "Root domain already managed by an existing Route53 hosted zone (e.g. \"example.com\"). Not created by this configuration -- looked up via data source. The frontend is served from this apex domain directly (no subdomain) -- only the backend gets one, see api_subdomain."
  type        = string
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
  description = "Root EBS volume size -- OS + Docker images only now (Postgres' own data lives on the separate volume below, not here). AWS refuses to launch with a root volume smaller than the AMI's own root snapshot (data.aws_ami.al2023_arm64, main.tf) regardless of how little of it this app actually uses -- 30 is that AMI's current floor (RunInstances fails with InvalidBlockDeviceMapping below it); bump this if a future AMI ever raises that floor further. Destroyed if the instance itself is ever replaced/terminated (e.g. `terraform destroy`), same as any root volume -- fine here since nothing on it is unique data, unlike data_volume_size_gb below."
  type        = number
  default     = 30
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

variable "github_repository" {
  description = "GitHub repo GitHub Actions deploys from, as \"owner/repo\" -- scopes the OIDC trust policy so only workflow runs from this exact repo (on main) can assume the deploy role."
  type        = string
}

# GitHub's OIDC token `sub` claim for repos created after 2026-07-15 is immutable by design and always embeds
# both numeric ids (`repo:OWNER@OWNER_ID/REPO@REPO_ID:...`) -- there's no way to opt out of this format for a
# qualifying repo, so the trust policies (github_oidc.tf, github_oidc_terraform.tf) have to match it exactly, not
# just the plain "owner/repo" name. See https://github.blog/changelog/2026-04-23-immutable-subject-claims-for-github-actions-oidc-tokens/
# Find these via `gh api users/<owner> --jq .id` and `gh api repos/<owner>/<repo> --jq .id`, or read them straight
# out of a failed AssumeRoleWithWebIdentity CloudTrail event's userIdentity.principalId.
variable "github_owner_id" {
  description = "Numeric GitHub user/org id for the owner in github_repository -- see the comment above."
  type        = string
}

variable "github_repo_id" {
  description = "Numeric GitHub repository id for github_repository -- see the comment above."
  type        = string
}

variable "tf_state_bucket_name" {
  description = "Same bucket name as backend.hcl's `bucket` (see versions.tf) -- backend blocks can't reference variables, so the name has to be duplicated here too, purely to scope github_actions_terraform's own S3 permissions (github_oidc_terraform.tf) to the actual state bucket instead of a guess."
  type        = string
}

