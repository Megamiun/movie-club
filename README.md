# movie-club

A web app replacing Google Sheets for a weekly movie club. See [CLAUDE.md](CLAUDE.md) for the full domain
model, feature list, and coding conventions — this file is just about getting the app running.

## Tech stack

| Layer          | Choice                                               |
|----------------|------------------------------------------------------|
| Frontend       | Vite + React + TypeScript, Material UI (`frontend/`) |
| Backend        | Ktor (Kotlin), Gradle multi-module (`backend/`)      |
| Database       | PostgreSQL + Exposed ORM, Flyway migrations          |
| Auth           | Email/password (Argon2id) + JWT                      |
| Movie metadata | TMDB API                                             |
| Poster storage | AWS S3 (not yet wired up in code — see CLAUDE.md)    |

## Prerequisites

- **Docker** + Docker Compose (easiest way to run everything)
- For local (non-Docker) dev: **JDK 21**, **Node 22+**, and a local **PostgreSQL** instance
- A [TMDB](https://www.themoviedb.org/settings/api) account for an API key + read access token (movie/series
  metadata lookups won't work without one, but everything else does)

## Quick start (Docker Compose)

```bash
cp .env.example .env
# edit .env and fill in TMDB_API_KEY / TMDB_ACCESS_TOKEN at minimum

docker compose up --build
```

This brings up three services:

- `db` — Postgres 17 on `:5432`
- `backend` — Ktor API on `:8080`, runs Flyway migrations on startup
- `frontend` — the built React app served by nginx on `:5173`

Open **http://localhost:5173**. There's no seeded user by default — see [Seeding a dev user](#seeding-a-dev-user)
below to create one, or register through an invite token (see [docs/flows.md](docs/flows.md)).

> `VITE_API_BASE_URL` (default `http://localhost:8080`) is baked into the frontend's JS bundle **at build time**,
> since it runs in the browser rather than inside the Compose network. If you change the backend's published port
> or host, rebuild the frontend image with the matching `VITE_API_BASE_URL` build arg.

## Local development (hot reload)

Running backend and frontend directly gives you hot reload; only the database needs Docker.

```bash
cp .env.example .env
# fill in TMDB_API_KEY / TMDB_ACCESS_TOKEN

docker compose up -d db
```

**Backend** (http://localhost:8080):

```bash
set -a && source .env && set +a
./gradlew :backend:run
```

**Frontend** (http://localhost:5173, proxies to the backend via `VITE_API_BASE_URL`):

```bash
cd frontend
cp .env.example .env   # defaults to http://localhost:8080, matches the backend above
npm install
npm run dev
```

## Environment variables

All defined in [.env.example](.env.example):

| Variable                                                                        | Purpose                                                                                                                                 |
|---------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD`                          | Postgres connection. Defaults match `docker compose up db`.                                                                             |
| `JWT_SECRET`                                                                    | Signing secret for auth tokens. Change for anything beyond local dev.                                                                   |
| `TMDB_API_KEY` / `TMDB_ACCESS_TOKEN`                                            | From [TMDB's API settings](https://www.themoviedb.org/settings/api). The access token (v4, Bearer) is what's actually used for lookups. |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_REGION` / `S3_BUCKET_NAME` | Reserved for poster storage — the S3 SDK is a dependency but no code uses it yet.                                                       |
| `VITE_API_BASE_URL`                                                             | Backend URL baked into the frontend build. See the note above.                                                                          |

## Seeding a dev user

The only way to create an account is via an invite token, which normally requires an already-authenticated admin
(chicken-and-egg for a fresh database). [docs/dummy-user.sql](docs/dummy-user.sql) inserts one fully-registered
member directly:

```bash
PGPASSWORD=postgres psql -h localhost -U postgres -d movieclub -f docs/dummy-user.sql
```

Logs in as `admin@example.com` / `hunter2`. From there, use the app's "Invite" flow to add real members.

## Testing & linting

**Backend** (needs Docker running — integration tests spin up a real Postgres via Testcontainers):

```bash
./gradlew :backend:test
./gradlew :backend:ktlintCheck   # or ktlintFormat to auto-fix
```

**Frontend**:

```bash
cd frontend
npm run build   # tsc -b + vite build — typechecks and builds
npm run lint     # oxlint
```

## API reference & sample data

- [docs/flows.md](docs/flows.md) — auth flow (invite → register → login) with example requests/responses
- `docs/*.http` files (`auth.http`, `club.http`, `meetings.http`, `series.http`, `watchlist.http`, `import.http`) —
  runnable request collections for IntelliJ's or VS Code's REST Client
- `samples/*.csv` — real-shaped data for the CSV importer (movies by year, series, watchlist backlog); see
  CLAUDE.md's "Existing Data" section for the column conventions

## Project structure

```
backend/    Ktor API — routing/, service/, db/ (Exposed repositories + Flyway migrations)
frontend/   Vite + React + MUI SPA
docs/       Auth flow write-up + .http request collections + dev-user seed SQL
samples/    Real CSV data for the importer
```
