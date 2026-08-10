resource "aws_security_group" "app" {
  name        = "movie-club-app"
  description = "movie-club EC2 instance (db+backend containers behind Caddy)"
  vpc_id      = data.aws_vpc.default.id

  # 80 is needed for Caddy's ACME HTTP-01 challenge, not just redirect-to-443 -- Let's Encrypt has to reach it.
  ingress {
    description = "HTTP (ACME challenge + redirect to HTTPS)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS (api.${var.domain_name})"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Interactive admin access only -- restricted to ssh_allowed_cidr, never open to the world. GitHub Actions
  # deploys via SSM Run Command (see github_oidc.tf), not SSH, precisely because a GitHub-hosted runner's IP is
  # dynamic and couldn't be allow-listed here safely.
  ingress {
    description = "SSH (admin only)"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
  }

  # Postgres (5432) is deliberately not exposed here at all -- the backend container reaches db over
  # docker-compose's own internal network, never through the host's public interface.

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "movie-club-app"
  }
}
