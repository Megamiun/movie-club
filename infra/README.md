# Infra

Terraform for the AWS deployment described in CLAUDE.md / TODO.md: a single EC2 instance running the `db` +
`backend` containers (Caddy on-box handles HTTPS for `api.<domain>`), and the frontend build published to S3
behind CloudFront, served from the apex `<domain>` itself (no subdomain). Runs in its own minimal VPC (`vpc.tf`)
-- one public subnet, an Internet Gateway, no NAT gateway -- rather than the account's default VPC, since nothing
here needs private-only networking (the db container has no subnet of its own to hide in, it shares the one
public EC2 instance with the backend).

This provisions infrastructure only -- it does not build or deploy the application itself. That's
`.github/workflows/deploy-backend.yml` / `deploy-frontend.yml`'s job, which need this Terraform applied first.

## One-time bootstrap: the state bucket

Terraform's own state lives in S3, but that bucket can't be created by the same configuration that depends on it
to store its state. Create it once, by hand, before the first `terraform init` -- pick any globally-unique bucket
name, in whichever region you like (the commands below use `movie-club-terraform-state` in `us-east-1` as an
example; substitute your own):

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

The bucket name/region aren't hardcoded in `versions.tf` -- backend blocks can't reference variables at all
(they're resolved before the rest of the config even exists), so both are supplied at `terraform init` time
instead, via
[partial backend configuration](https://developer.hashicorp.com/terraform/language/backend#partial-configuration):

```bash
cd infra
cp backend.hcl.example backend.hcl   # fill in the bucket name/region you created above, never commit this file
terraform init -backend-config=backend.hcl
```

## One-time bootstrap: the secrets

`jwt_secret`, `database_password`, `tmdb_access_token`, and `omdb_api_key` are **not** Terraform variables --
`ssm.tf` reads them as `data` sources, not `resource`s, so Terraform never creates or holds their plaintext (which
would otherwise sit in Terraform state -- `sensitive = true` on a variable only redacts CLI/log *output*, not the
state file itself). Create the four parameters by hand, once, before the first `terraform plan`/`apply`:

```bash
aws ssm put-parameter --name /movie-club/jwt_secret --type SecureString --value "$(openssl rand -base64 32)"
aws ssm put-parameter --name /movie-club/database_password --type SecureString --value "$(openssl rand -base64 32)"
aws ssm put-parameter --name /movie-club/tmdb_access_token --type SecureString --value "your-tmdb-api-read-access-token"
aws ssm put-parameter --name /movie-club/omdb_api_key --type SecureString --value "your-omdb-api-key"  # optional -- can be an empty string
```

The parameter *names* (`/movie-club/jwt_secret`, etc.) are fixed by `ssm.tf`'s `ssm_parameter_prefix` local -- if
you rename that, update these commands to match. Rotating a value later is just re-running the matching
`put-parameter` command (`aws ssm put-parameter ... --overwrite`); nothing in Terraform needs to change.

## Deploying

```bash
cp terraform.tfvars.example terraform.tfvars   # fill in real values, never commit this file
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

| Variable                     | Value                                                  |
|------------------------------|--------------------------------------------------------|
| `AWS_DEPLOY_ROLE_ARN`        | `terraform output -raw github_actions_deploy_role_arn` |
| `EC2_INSTANCE_ID`            | `terraform output -raw ec2_instance_id`                |
| `API_BASE_URL`               | `https://api.<domain_name>`                            |
| `CLOUDFRONT_DISTRIBUTION_ID` | `terraform output -raw cloudfront_distribution_id`     |

Also make sure `github_repository` in `terraform.tfvars` is set (`"owner/repo"`) -- the OIDC trust policy only
allows the role to be assumed from a push to *this exact repo's* `main` branch, nothing broader.

`ssh_public_key`/`ssh_allowed_cidr` are for your own interactive admin access only (`ssh ec2-user@<ec2_public_ip>`)
-- unrelated to what GitHub Actions needs, since a GitHub-hosted runner's dynamic IP could never satisfy a fixed
`ssh_allowed_cidr` anyway.

## Applying via CI/CD instead of locally

After the one-time local bootstrap above, `.github/workflows/terraform.yml` can take over. `validate` (fmt +
`terraform validate`, no AWS credentials) runs automatically on every push and PR touching `infra/**`. `plan` and
`apply` are manual-only -- triggered by running the workflow by hand (Actions tab → "Run workflow", against
`main`), not automatically on push -- so real infra state is never touched without someone deliberately asking
for it. `apply` also still needs the repo's `production` GitHub Environment approval on top of that (create it in
Settings → Environments with at least one required reviewer), so a human confirms the actual plan output before
it applies, even after triggering the run. Set these in Settings → Secrets and variables → Actions:

**Variables**:

| Variable                 | Value                                                                                                                                                                            |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AWS_TERRAFORM_ROLE_ARN` | `terraform output -raw github_actions_terraform_role_arn` (from the local bootstrap apply)                                                                                       |
| `TF_STATE_BUCKET`        | Same bucket name used in your own `backend.hcl` -- CI has no local `backend.hcl` file to read, so `terraform.yml` passes this straight through as both `-backend-config="bucket=..."` *and* `TF_VAR_tf_state_bucket_name` (the latter scopes `github_actions_terraform`'s own S3 permissions to this bucket, see `github_oidc_terraform.tf`) |
| `TF_STATE_REGION`        | Same region used in your own `backend.hcl` -- the state bucket's own region, unrelated to `aws_region`/`DOMAIN_NAME`'s infra                                                     |
| `DOMAIN_NAME`            | Same value as `domain_name` in `terraform.tfvars`                                                                                                                                |
| `SSH_PUBLIC_KEY`         | Same value as `ssh_public_key` in `terraform.tfvars` -- it's a public key, not sensitive                                                                                         |
| `SSH_ALLOWED_CIDR`       | Same value as `ssh_allowed_cidr` in `terraform.tfvars`                                                                                                                           |

**No GitHub Secrets are needed for this workflow at all** -- `jwt_secret`/`database_password`/`tmdb_access_token`/
`omdb_api_key` aren't Terraform variables anymore (see "One-time bootstrap: the secrets" above), so there's
nothing sensitive left to pass in as a `TF_VAR_*`. `github_repository` doesn't need setting either -- the workflow
passes `github.repository` (GitHub's own built-in context) directly.

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
  not RDS. Its data lives on its own separate EBS volume (`aws_ebs_volume.postgres_data`, `ec2.tf`), kept apart
  from the instance's root volume specifically so it survives independently of the instance itself -- but it's
  still destroyed if that volume is ever removed or `terraform destroy` is run (guarded by `prevent_destroy`, see
  `ec2.tf`'s comment) -- know that before you destroy.
