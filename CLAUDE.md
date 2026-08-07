# movie-club

A web app replacing Google Sheets for a weekly movie club. Supports multiple independent **Clubs** (isolated friend groups), each with their own members, schedule, and history.

## Tech Stack

| Layer | Choice |
|---|---|
| Frontend | Vite + React + TypeScript (SPA, JSON REST API, no SSR) |
| Backend | Ktor (Kotlin) |
| Database | PostgreSQL + Exposed ORM |
| Auth | Email/password (Argon2id) + JWT (7-day) + invite-token registration |
| Movie metadata | TMDB API (lookup by IMDB `tt` ID via `/find` endpoint) |
| Poster storage | AWS S3 (MinIO for local dev) |

## Domain Model

### Club
- Isolated group with N members, each with a role (≥1 admin)
- Has a rotation order (ordered member list, used only at schedule generation time)
- Has two configurable rating scales: **quality** and **sentiment**
- Default quality scale: Excepcional!, Muito bom, Bom, Regular, Ruim, Horrível
- Default sentiment scale: Adorei, Gostei!, Ambivalente, Indiferente, Desgostei, Detestei

### Member
- Authenticates via email/password; accounts created only through invite tokens
- Belongs to one or more Clubs with a role
- Has a personal **Watchlist** per Club (visible to all club members)

### Meeting
- An event with a date
- Optional `assigned_member` (whose round-robin slot it is; null if shared/merged)
- Owns movies and series episodes
- Meetings can be merged (move movies, delete empty one), postponed (change date), or split

### Movie
- Belongs to a Meeting; `chosen_by` tracks who picked it (any member can add to any meeting)
- Added via IMDB URL → extract `tt` ID → TMDB API fetch
- Cached TMDB metadata: `original_title`, `english_title`, `custom_title`, `display_title` preference (ORIGINAL|ENGLISH|CUSTOM, default ORIGINAL), year, director, runtime, genre, country, TMDB rating, poster (stored in S3), `metadata_fetched_at`
- Metadata can be manually refreshed (for unreleased films with missing fields)
- Separate "where to watch" link (e.g. HBO, Netflix, magnet link)
- Per-member **ratings**: quality scale + sentiment scale (both optional)
- Per-member **comments** (free text, optional)

### Series → Season → Episode
- Parallel side track; runs alongside movies, can share the same meeting dates
- `chosen_by` at series level
- Ratings optional at series, season, and episode level
- Episodes assigned to Meeting dates (multiple episodes per meeting)

### WatchlistEntry
- Personal per member per Club; visible to all club members

### RatingScale
- Two per Club (quality + sentiment); ordered list of configurable labels
- Seeded with Portuguese defaults at Club creation

## Schedule Model

- Schedule is pre-generated for a full year at a time (done at year-end for the coming year)
- Each Meeting has an optional `assigned_member` derived from the round-robin rotation
- The rotation order is a simple ordered member list on Club, used only at generation time — not enforced at runtime
- No separate Turn/Slot entity; Meeting is the primary scheduling unit

### Key scenarios

| Scenario | Model |
|---|---|
| Normal week | `Meeting(assigned_member=G)` → one movie `chosen_by=G` |
| Multiple movies, one person | Meeting → two movies both `chosen_by=G` |
| Extra pick by another member | `Meeting(assigned_member=G)` → movies by G and C |
| Merged meeting (two turns, one date) | `Meeting(assigned_member=null)` → movies `chosen_by=G` and `chosen_by=C` |
| Swap | Reassign `assigned_member` on two meetings |
| Postpone | Change `Meeting.date` |
| Future empty slot | Meeting exists with no movies yet |

## Features (v1)

- Schedule view (past and upcoming meetings)
- Movie and series management per meeting
- Ratings + comments entry (mobile-responsive — used on the couch)
- Personal watchlist per member
- Stats/charts (genres, rating comparisons, etc.)
- CSV importer for existing 2025/2026/2027 data
- Year-at-a-time schedule generation
- No push notifications

## Existing Data

Sample CSVs in `samples/`:
- `Movie Club - Movies 2025.csv` / `2026.csv` / `2027.csv` — main schedule and ratings
- `Movie Club - Series.csv` — series episodes with watch dates and ratings
- `Movie Club - Reserve.csv` — per-member watchlist backlog

CSV conventions:
- `Choice` column: member initial (G/C) who chose the movie
- Blank `When?` inherits the date from the row above
- `IMDB Id` column: the `tt` identifier used for TMDB lookup