# Infra

Terraform for the AWS deployment described in CLAUDE.md / TODO.md: a single EC2 instance running the `db` +
`backend` containers (Caddy on-box handles HTTPS for `api.<domain>`), and the frontend build published to S3
behind CloudFront (`app.<domain>`).

This provisions infrastructure only -- it does not build or deploy the application itself. That's
`.github/workflows/deploy-backend.yml` / `deploy-frontend.yml`'s job, which need this Terraform applied first.

## One-time bootstrap: the state bucket

Terraform's own state lives in S3 (`versions.tf`), but that bucket can't be created by the same configuration
that depends on it to store its state. Create it once, by hand, before the first `terraform init`:

```bash
aws s3api create-bucket --bucket movie-club-terraform-state --region us-east-1
aws s3api put-bucket-versioning --bucket movie-club-terraform-state \
  --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket movie-club-terraform-state \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
aws s3api put-public-access-block --bucket movie-club-terraform-state \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

State locking uses Terraform's native S3 lockfile support (`use_lockfile = true`, needs Terraform >= 1.10) --
no DynamoDB table needed.

## Deploying

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars   # fill in real values, never commit this file
terraform init
terraform plan
terraform apply
```

Requires an AWS account with an existing Route53 hosted zone for `domain_name` -- this configuration doesn't
create the zone itself, only records inside it.

**This first apply has to be run locally, with your own AWS credentials** -- it's what creates
`github_actions_terraform`, the IAM role `.github/workflows/terraform.yml` needs to run itself. CI can't apply the
config that creates its own credentials before that credential exists. Every apply *after* this first one can run
either locally or via CI.

## Wiring up GitHub Actions after applying

The workflows authenticate to AWS via OIDC (`infra/github_oidc.tf`) -- no AWS access keys stored in GitHub at
all. After `terraform apply`, set these in the repo's Settings → Secrets and variables → Actions:

**Variables** (not secret -- an IAM role ARN, an instance id, and resource ids aren't sensitive by themselves).
No GitHub *Secrets* are needed at all -- the backend deploy step authenticates via SSM Run Command under the same
OIDC-assumed role, not SSH, so there's no private key to store:

| Variable                     | Value                                              |
|-------------------------------|-----------------------------------------------------|
| `AWS_DEPLOY_ROLE_ARN`          | `terraform output -raw github_actions_deploy_role_arn` |
| `EC2_INSTANCE_ID`              | `terraform output -raw ec2_instance_id`            |
| `API_BASE_URL`                 | `https://api.<domain_name>`                        |
| `S3_FRONTEND_BUCKET`           | `terraform output -raw s3_frontend_bucket`         |
| `CLOUDFRONT_DISTRIBUTION_ID`   | `terraform output -raw cloudfront_distribution_id` |

Also make sure `github_repository` in `terraform.tfvars` is set (`"owner/repo"`) -- the OIDC trust policy only
allows the role to be assumed from a push to *this exact repo's* `main` branch, nothing broader.

`ssh_public_key`/`ssh_allowed_cidr` are for your own interactive admin access only (`ssh ec2-user@<ec2_public_ip>`)
-- unrelated to what GitHub Actions needs, since a GitHub-hosted runner's dynamic IP could never satisfy a fixed
`ssh_allowed_cidr` anyway.

## Applying via CI/CD instead of locally

After the one-time local bootstrap above, `.github/workflows/terraform.yml` can take over: `plan` runs on every
push to main that touches `infra/**`, `apply` runs after, gated behind the repo's `production` GitHub Environment
-- create that environment in Settings → Environments with at least one required reviewer, so a human has to
approve before `apply` actually runs (the plan output is already sitting in the `plan` job's own log by then, for
the approver to check against what's about to happen). Set these in Settings → Secrets and variables → Actions:

**Variables**:

| Variable                 | Value                                                      |
|-----------------------------|---------------------------------------------------------------|
| `AWS_TERRAFORM_ROLE_ARN`    | `terraform output -raw github_actions_terraform_role_arn` (from the local bootstrap apply) |
| `DOMAIN_NAME`               | Same value as `domain_name` in `terraform.tfvars`          |
| `SSH_PUBLIC_KEY`            | Same value as `ssh_public_key` in `terraform.tfvars` -- it's a public key, not sensitive |
| `SSH_ALLOWED_CIDR`          | Same value as `ssh_allowed_cidr` in `terraform.tfvars`      |

**Secrets** (the four sensitive `terraform.tfvars` values):

| Secret                        | Value                                  |
|----------------------------------|-------------------------------------------|
| `TF_VAR_JWT_SECRET`              | Same value as `jwt_secret`             |
| `TF_VAR_DATABASE_PASSWORD`       | Same value as `database_password`      |
| `TF_VAR_TMDB_ACCESS_TOKEN`       | Same value as `tmdb_access_token`      |
| `TF_VAR_OMDB_API_KEY`            | Same value as `omdb_api_key`           |

`github_repository` doesn't need setting here -- the workflow passes `github.repository` (GitHub's own built-in
context) directly.

**Why a second, separate role from the app-deploy one** (`AWS_DEPLOY_ROLE_ARN`, used by `deploy-backend.yml`/
`deploy-frontend.yml`): running Terraform itself needs to create/modify IAM roles, security groups, DNS, ACM
certs -- a meaningfully larger blast radius than "push an image, sync a bucket." Keeping them as separate IAM
roles means a compromise of one doesn't hand over the other. `github_actions_terraform`'s trust policy is also
narrower in a different way: only a push to main, never `pull_request` at all (see
`github_oidc_terraform.tf`'s comment) -- this repo is public, and trusting a role from `pull_request` is a known
OIDC risk for public repos (the workflow file for that event is sourced from the PR's own branch, so anyone
opening a PR could rewrite it to abuse a role trusted at that trigger). PRs still get `terraform fmt`/`validate`
feedback from the `validate` job, which needs no AWS credentials at all.

## What's NOT here

- **The app deploy itself** -- pushing a new backend image and refreshing the frontend build is GitHub Actions'
  job, not Terraform's. Re-running `terraform apply` doesn't redeploy the application.
- **A managed database** -- `db` runs as a container on the same EC2 instance (see `templates/user_data.sh.tpl`),
  not RDS. Its data lives on the instance's root EBS volume, which persists across stop/start and reboot but is
  destroyed if the instance itself is ever replaced or `terraform destroy` is run -- know that before you destroy.
- **A custom VPC** -- everything lives in the account's default VPC/subnet; standing up a dedicated one (NAT
  gateway, route tables, etc.) is pure added cost for a single box.
