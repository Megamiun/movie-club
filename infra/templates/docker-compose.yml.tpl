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
