resource "aws_key_pair" "deploy" {
  key_name   = "movie-club-deploy"
  public_key = var.ssh_public_key
}

# Separate from the instance's root volume specifically so Postgres' own data survives independently of it --
# a root-volume replacement or instance-type change no longer risks the data at all. prevent_destroy is a
# deliberate speed bump: a `terraform destroy` (or removing aws_instance.app in a way that forces replacement)
# will refuse to delete this volume until that lifecycle block is removed by hand, so wiping the club's data is
# never a side effect of an unrelated infra change.
resource "aws_ebs_volume" "postgres_data" {
  availability_zone = aws_subnet.public.availability_zone
  size              = var.data_volume_size_gb
  type              = "gp3"
  encrypted         = true

  tags = {
    Name = "movie-club-postgres-data"
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_instance" "app" {
  ami                    = data.aws_ami.al2023_arm64.id
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.app.id]
  key_name               = aws_key_pair.deploy.key_name
  iam_instance_profile   = aws_iam_instance_profile.ec2.name

  root_block_device {
    volume_size = var.root_volume_size_gb
    volume_type = "gp3"
    # OS + Docker images only now -- Postgres' own data lives on the separate aws_ebs_volume.postgres_data above.
    # Encrypted at rest with the default AWS-managed EBS key either way, no extra cost or performance cost.
    encrypted = true
  }

  # Requires the token-based metadata flow (IMDSv2) -- without this, an SSRF-style bug in anything running on the
  # box could otherwise fetch the instance's IAM credentials from the metadata endpoint with a single unauthenticated
  # GET, no token needed.
  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  user_data = templatefile("${path.module}/templates/user_data.sh.tpl", {
    api_domain = local.api_domain
    aws_region = var.aws_region
    # Nitro-based instances (the t4g family) expose EBS volumes as NVMe devices whose /dev/nvmeXn1 enumeration
    # order isn't guaranteed to match attachment order -- /dev/disk/by-id/nvme-Amazon_Elastic_Block_Store_<id>
    # (id with its dash stripped) is the reliable way to find this specific volume, per AWS's own guidance.
    data_volume_device_id = replace(aws_ebs_volume.postgres_data.id, "-", "")
  })

  tags = {
    Name = "movie-club-app"
  }

  # data.aws_ami.al2023_arm64 uses most_recent = true, so it can resolve to a different AMI id on any given plan
  # purely because AWS published a new AL2023 build since the last apply -- without this, that alone would force
  # an unplanned replacement (destroy + recreate, ~1-2 min of reinstalling Docker/Caddy/re-pulling the backend
  # image) as a side effect of a completely unrelated change. Ignoring drift on `ami` means the instance still
  # launches on whatever's most recent the *first* time, but never gets replaced again just because a newer AMI
  # exists -- only a deliberate change elsewhere (subnet, instance_type, etc.) still triggers a real replacement.
  #
  # user_data is ignored for a different reason: it doesn't force a *replacement* like ami would, but AWS still
  # requires stopping the instance to modify it in place, so every user_data.sh.tpl edit was cycling the live
  # instance's power (stop -> modify -> start) on every apply -- confirmed live via CloudTrail after a routine
  # template fix unexpectedly took the whole stack down (db has no restart policy at the time, so it stayed down
  # after the reboot). That disruption bought nothing: user_data only ever runs once, at an instance's first boot
  # (see the template's own comment), so re-applying it to an already-booted instance never actually executes the
  # updated script anyway. A template change now only takes effect the next time the instance is genuinely
  # replaced for some other reason, never by power-cycling the current one for its own sake.
  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

resource "aws_volume_attachment" "postgres_data" {
  device_name = "/dev/sdf" # a hint only on Nitro instances -- see the by-id comment above for how it's really found
  volume_id   = aws_ebs_volume.postgres_data.id
  instance_id = aws_instance.app.id
}

# A stable public IP survives the instance stopping/starting (its ephemeral public IP wouldn't) -- Route53's
# api_domain record points here, so a reboot doesn't silently break DNS until the next terraform apply.
resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"

  tags = {
    Name = "movie-club-app"
  }
}
