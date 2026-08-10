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
  availability_zone = data.aws_subnet.selected.availability_zone
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
  subnet_id              = data.aws_subnet.selected.id
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
