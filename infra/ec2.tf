resource "aws_key_pair" "deploy" {
  key_name   = "movie-club-deploy"
  public_key = var.ssh_public_key
}

resource "aws_instance" "app" {
  ami                    = data.aws_ami.al2023_arm64.id
  instance_type          = var.instance_type
  subnet_id              = data.aws_subnets.default.ids[0]
  vpc_security_group_ids = [aws_security_group.app.id]
  key_name               = aws_key_pair.deploy.key_name
  iam_instance_profile   = aws_iam_instance_profile.ec2.name

  root_block_device {
    volume_size = var.root_volume_size_gb
    volume_type = "gp3"
  }

  user_data = templatefile("${path.module}/templates/user_data.sh.tpl", {
    api_domain = local.api_domain
    aws_region = var.aws_region
  })

  tags = {
    Name = "movie-club-app"
  }
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
