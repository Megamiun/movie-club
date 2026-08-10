#!/bin/bash
# Provisions the box: Docker, the Compose CLI plugin, Caddy (automatic HTTPS reverse proxy in front of the
# backend container), and a starter docker-compose.yml under /opt/movie-club. Deploys themselves (pulling a new
# backend image, refreshing the .env from SSM, `docker compose up -d`) are GitHub Actions' job over SSH -- this
# script only needs to run once, at first boot.
set -euo pipefail

dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user

# Amazon Linux 2023's docker package doesn't ship the compose plugin -- install the official CLI plugin binary
# directly, matching this instance's own architecture (arm64 by default, see variables.tf's instance_type).
COMPOSE_VERSION="v2.32.4"
ARCH="$(uname -m)"
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/$COMPOSE_VERSION/docker-compose-linux-$ARCH" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Caddy: no AL2023 package, install the official static binary directly.
CADDY_ARCH="$([ "$ARCH" = "aarch64" ] && echo arm64 || echo amd64)"
curl -fsSL "https://github.com/caddyserver/caddy/releases/latest/download/caddy_2.9.1_linux_$${CADDY_ARCH}.tar.gz" \
  -o /tmp/caddy.tar.gz
tar -xzf /tmp/caddy.tar.gz -C /usr/local/bin caddy
rm /tmp/caddy.tar.gz

mkdir -p /etc/caddy
cat > /etc/caddy/Caddyfile <<'CADDYFILE'
${api_domain} {
	reverse_proxy localhost:8080
}
CADDYFILE

cat > /etc/systemd/system/caddy.service <<'UNIT'
[Unit]
Description=Caddy
After=network.target

[Service]
ExecStart=/usr/local/bin/caddy run --config /etc/caddy/Caddyfile
ExecReload=/usr/local/bin/caddy reload --config /etc/caddy/Caddyfile
Restart=on-failure
User=root

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable --now caddy

mkdir -p /opt/movie-club
cat > /opt/movie-club/docker-compose.yml <<'COMPOSE'
services:
  db:
    image: postgres:17
    environment:
      POSTGRES_DB: movieclub
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: $${DATABASE_PASSWORD}
    volumes:
      - db_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d movieclub"]
      interval: 5s
      timeout: 5s
      retries: 5

  backend:
    image: $${BACKEND_IMAGE}
    ports:
      - "8080:8080"
    environment:
      DATABASE_URL: jdbc:postgresql://db:5432/movieclub
      DATABASE_USER: postgres
      DATABASE_PASSWORD: $${DATABASE_PASSWORD}
      JWT_SECRET: $${JWT_SECRET}
      TMDB_ACCESS_TOKEN: $${TMDB_ACCESS_TOKEN}
      OMDB_API_KEY: $${OMDB_API_KEY}
    depends_on:
      db:
        condition: service_healthy
    restart: unless-stopped

volumes:
  db_data:
COMPOSE

# Regenerates .env from SSM Parameter Store -- run by the GitHub Actions deploy step before every
# `docker compose up -d`, so secrets are fetched fresh at deploy time rather than living in this file at rest.
cat > /opt/movie-club/fetch-secrets.sh <<'SCRIPT'
#!/bin/bash
set -euo pipefail
region="${aws_region}"
prefix="/movie-club"
get() { aws ssm get-parameter --region "$region" --name "$prefix/$1" --with-decryption --query Parameter.Value --output text; }
cat > /opt/movie-club/.env <<ENV
DATABASE_PASSWORD=$(get database_password)
JWT_SECRET=$(get jwt_secret)
TMDB_ACCESS_TOKEN=$(get tmdb_access_token)
OMDB_API_KEY=$(get omdb_api_key)
ENV
chmod 600 /opt/movie-club/.env
SCRIPT
chmod +x /opt/movie-club/fetch-secrets.sh
