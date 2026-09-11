services:
  db:
    image: postgres:17
    environment:
      POSTGRES_DB: movieclub
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: $${DATABASE_PASSWORD}
      # A freshly-formatted ext4 filesystem always has a lost+found directory at its root (mkfs.ext4's own
      # doing) -- initdb refuses to treat a non-empty directory as its data dir, even though the only thing in
      # it is that. Pointing PGDATA at a subdirectory of the mount instead of the mount root itself is postgres's
      # own documented fix for exactly this "mounting a volume directly as the data directory" scenario.
      PGDATA: /var/lib/postgresql/data/pgdata
    volumes:
      - /mnt/postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d movieclub"]
      interval: 5s
      timeout: 5s
      retries: 5
    # Unlike backend below, this had no restart policy at all until this line -- if db is ever stopped for any
    # reason (a signal from outside docker-compose, a host reboot without a full re-up, etc.), it just stayed
    # stopped indefinitely, while backend kept crash-looping against a database that was never coming back on its
    # own. Found live: db exited cleanly (received a fast shutdown request, no crash/OOM/disk issue) but simply
    # never restarted, and backend's own restart count had climbed into the hundreds by the time this was noticed.
    restart: unless-stopped

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
      # Not secret (unlike the above, all sourced from SSM via fetch-secrets.sh's own .env) -- the bucket name is
      # baked in directly by Terraform instead, same as awslogs-region/awslogs-group below, since there's no
      # reason to round-trip a plain resource name through SSM Parameter Store.
      S3_BUCKET_NAME: ${s3_bucket_name}
    depends_on:
      db:
        condition: service_healthy
    restart: unless-stopped
    # Ships stdout/stderr to CloudWatch Logs (infra/cloudwatch.tf) instead of only the docker daemon's own local
    # log buffer, which is lost the moment this container restarts (crash-loop, redeploy). Uses the instance's own
    # IAM role (iam.tf's cloudwatch_write_backend_logs) for credentials -- nothing to configure here for that.
    # awslogs-create-group is false since the group already exists (Terraform-managed); the instance role is
    # deliberately not granted logs:CreateLogGroup at all, so leaving this at its true default would just fail.
    logging:
      driver: awslogs
      options:
        awslogs-region: "${aws_region}"
        awslogs-group: "${log_group}"
        awslogs-create-group: "false"
        awslogs-stream: "backend"

  # Scrapes the backend's own GET /metrics (unauthenticated, see CLAUDE.md's Backend Architecture section) --
  # never exposed to Caddy/the internet itself, only reachable from grafana below over this compose network.
  # mem_limit is deliberate: this box is a t4g.small (2GB total, see variables.tf's instance_type), already
  # shared with Postgres and the JVM backend, so an unbounded Prometheus/Grafana is a real risk of starving
  # either of them under memory pressure rather than just failing on its own.
  prometheus:
    image: prom/prometheus:v3.14.0
    mem_limit: 256m
    volumes:
      - prometheus_data:/prometheus
      - /opt/movie-club/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    command:
      - --config.file=/etc/prometheus/prometheus.yml
      # Bounds disk usage on the root volume (OS + Docker images only otherwise, see ec2.tf's root_block_device
      # comment) -- a small club's metrics volume has no real need for longer retention than this.
      - --storage.tsdb.retention.time=15d
    restart: unless-stopped

  # The only piece of this stack meant for a human to actually open -- reached via Caddy on its own subdomain
  # (metrics.<domain>, see infra/dns.tf), same TLS-terminate-then-reverse-proxy shape as backend above.
  grafana:
    image: grafana/grafana:13.2.1
    mem_limit: 256m
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      # Sourced from SSM via fetch-secrets.sh's own .env, same pattern as JWT_SECRET/DATABASE_PASSWORD above --
      # never baked in at rest.
      GF_SECURITY_ADMIN_PASSWORD: $${GRAFANA_ADMIN_PASSWORD}
      # Not secret (like S3_BUCKET_NAME above) -- baked in directly by Terraform since it's just this
      # deployment's own subdomain, not something that needs to round-trip through SSM.
      GF_SERVER_ROOT_URL: https://${metrics_domain}
    volumes:
      - grafana_data:/var/lib/grafana
      - /opt/movie-club/grafana-datasources.yml:/etc/grafana/provisioning/datasources/datasources.yml:ro
    depends_on:
      - prometheus
    restart: unless-stopped

volumes:
  # Plain Docker-managed volumes (unlike Postgres' own dedicated EBS volume) -- metrics are observability data,
  # not the club's source of truth, so losing them to an instance replacement is an acceptable trade-off against
  # the added complexity of another dedicated EBS volume + mount for this.
  prometheus_data:
  grafana_data:
