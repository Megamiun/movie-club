# movie-club

A web app replacing Google Sheets for a weekly movie club. Supports multiple independent **Clubs** (isolated friend
groups), each with their own members, schedule, and history.

## Tech Stack

| Layer          | Choice                                                              |
|----------------|---------------------------------------------------------------------|
| Frontend       | Vite + React + TypeScript (SPA, JSON REST API, no SSR)              |
| Backend        | Ktor (Kotlin)                                                       |
| Database       | PostgreSQL + Exposed ORM                                            |
| Auth           | Email/password (Argon2id) + JWT (7-day) + invite-token registration |
| Movie metadata | TMDB API (lookup by IMDB `tt` ID via `/find` endpoint)              |
| Poster storage | AWS S3 (MinIO for local dev)                                        |

## Coding Conventions

Mechanical/formatting rules (indentation, import order, trailing commas, line length) belong in `.editorconfig` — ktlint
enforces those automatically. This section is for conventions ktlint can't check.

- **Don't use `?.let { }` as a null check.** If the block is just conditionally executing code (not producing a value
  you need as an expression), use `if (x != null) { ... }` instead. Reserve `?.let` for chains that actually
  transform/map the non-null value into a result.
- **Import enum constants directly and reference them unqualified**, e.g.
  `import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY` then use `QUALITY`, rather than writing
  `RatingScaleType.QUALITY` inline — unless that name would conflict with another imported symbol in the same file, in
  which case fall back to the qualified form.
- **Don't parse a string into an enum with `Enum.valueOf(...)`.** It throws on a bad match, so parsing untrusted input
  means wrapping it in `runCatching` just to swallow the exception. Use `Enum.entries.find { it.name == input }`
  instead — it returns `null` on no match, no exception involved.
- **Don't declare an extension function on a class in the same file as that class, unless it's private/file-local or
  genuinely doesn't fit as a member.** If you own the class, can already touch its body, and the function is meant to be
  part of its public surface, an extension function buys nothing over a member function — make it a member instead.
  Extension functions stay fine for: types declared elsewhere (stdlib types, another module's classes, the same type
  from a different file), functions only ever called within that same file, and cases where membership genuinely doesn't
  fit (e.g. it would pull an unwanted dependency into the class itself).
- **Give every nullable parameter a `= null` default** — in data class constructors (rows/DTOs) and in function/method
  signatures alike, including the trailing parameter and regardless of whether the value is semantically "expected to be
  missing" or an explicit "clear this" null. This applies everywhere except `@Serializable` request/response DTOs in
  `routing/`, where a missing default changes wire behavior (whether the field can be omitted from JSON) rather than
  just caller convenience. For interface methods implemented elsewhere (e.g. `db.repositories` interfaces backed by
  `db.repositories.exposed` classes), put the default only on the interface — Kotlin forbids an `override fun` from
  redeclaring a default. Don't pass an explicit `null` at a call site for a parameter that already defaults to `null`;
  drop the argument, switching to named arguments for whatever comes after it if needed to keep the call unambiguous.

## Backend Architecture

- Each domain's repository is split across three packages: `db.repositories` holds the interface only,
  `db.repositories.dto` holds its data classes (`*Row`/`*Dto`), `db.repositories.exposed` holds the `Exposed*Repository`
  implementation. Chosen specifically so the interface keeps its original package — most service/route callers only ever
  reference the interface and DTOs, so this split needed near-zero import churn versus moving everything.
- Repository integration tests use Testcontainers (`org.testcontainers:postgresql`, pinned to 1.21.4 — the `postgresql`
  artifact isn't published for the 2.x line), one fresh Postgres container per test class via
  `companion object { init { TestDatabase.startFresh() } }`. The companion-object `init` block runs exactly once per
  class regardless of JUnit4 creating a new test instance per `@Test` method. This relies on Gradle running tests
  sequentially (the default): Exposed's no-arg `transaction {}` uses one process-wide "current" database, so parallel
  test classes would race on it.
- Repositories never depend on other repositories, even across domains that reference each other (e.g. Movie and
  MediaItem). Cross-entity orchestration — creating a MediaItem alongside a Movie catalog row, then linking them —
  happens in the service layer, which already composes multiple repositories plus TmdbClient/OmdbClient. Keeps each
  repository a simple, independently testable mapping onto its own table(s).

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

### MediaItem

- Universal handle for anything sourced from TMDB — `type` (MOVIE|SERIES|EPISODE; EPISODE is reserved but not
  populated yet, see below), deduplicated by `imdb_id`, plus `tmdb_id`, `title`, `year`, `poster_url` (an external
  TMDB CDN URL string, unrelated to Movie/Series' own long-unused `poster_s3_key`), TMDB rating, and IMDB rating
- Created *only* by a successful TMDB lookup (search result, or an IMDB id/URL resolved through TMDB) — never from
  freeform/manually-typed input. This is a deliberate policy, not just today's implementation default: nothing
  should ever reference a MediaItem that TMDB couldn't derive
- Movie and Series catalog rows each carry a `media_item_id` pointing at their MediaItem, created/refreshed
  alongside their own richer columns (director/runtime for Movie, creator for Series — those stay on Movie/Series
  themselves; MediaItem isn't a replacement for the full catalog row, just a shared cross-type reference point)
- Episode does **not** have a `media_item_id` yet: TMDB only exposes an episode's own `imdb_id` via a separate
  per-episode `external_ids` call this app has never made (episodes are looked up by `(series, season, episode
  number)`, not a standalone id). Wiring that up is its own follow-up, not bundled into MediaItem's introduction —
  see the Episode TMDB lookup note below for why episode enrichment already tolerates missing external data
- WatchlistEntry references a MediaItem directly instead of duplicating title/year/rating itself (see below)
- IMDB's own rating is fetched separately from OMDb (`OmdbClient`, `OMDB_API_KEY`) since TMDB's API never exposes it
  (only its own `vote_average`) — optional, silently no-ops when the key is unset, never blocks an add/refresh.
  Wherever a rating is displayed, the UI prefers IMDB's over TMDB's, falling back cleanly for rows fetched before
  OMDb was wired in

### Movie

- Split into a **global catalog row** (deduplicated by `imdb_id`, shared by every club that picks the same real movie)
  and a **per-meeting pick row** (`chosen_by`, `custom_title`, `display_title_preference`, `watch_link` — the
  club-specific facts about one meeting's choice). The pick's id is the externally-visible "movie id" everywhere (
  routes, reviews) — the catalog row is an internal implementation detail most code never sees directly.
- Any member can add to any meeting; adding by `imdb_id` reuses the existing catalog row if another club (or an earlier
  meeting) already picked it, refetching nothing
- Added via IMDB URL → extract `tt` ID → TMDB API fetch, or via title search (`GET /movies/search`, picks by
  `tmdb_id`) — either path resolves through TMDB, so either way the catalog row also gets a linked MediaItem (see
  above)
- Cached TMDB metadata (on the catalog row): `original_title`, `alternative_titles` (list of per-country titles from
  TMDB, replaces the old single `english_title` — each entry has a country code, the title, and TMDB's own `type`
  classification like "working title"/"festival title", blank/omitted types stored as `null`), year, director, runtime,
  genre, `origin_country`, `production_countries` (a second, distinct country list — TMDB's full production-country
  objects, not just origin codes), TMDB rating, IMDB rating (via OMDb, see MediaItem above), poster (stored in S3),
  `metadata_fetched_at`.
  `display_title_preference` (ORIGINAL|ENGLISH|CUSTOM, default ORIGINAL) lives on the pick row; resolving what "ENGLISH"
  means from `alternative_titles` isn't done server-side yet.
- Metadata can be manually refreshed (for unreleased films with missing fields) — refreshing from any one pick updates
  the shared catalog row for every other pick of the same movie
- Separate "where to watch" link (e.g. HBO, Netflix, magnet link) — per-meeting-pick, not global
- Per-member **ratings**: quality scale + sentiment scale (both optional) — keyed to the per-meeting pick, not the
  global movie, since the same movie rewatched at a later meeting can get a fresh rating
- Per-member **comments** (free text, optional)
- Deleting a pick removes only that meeting's choice; the shared catalog row (and any other club's pick of it) is
  untouched

### Series → Season → Episode

- Parallel side track; runs alongside movies, can share the same meeting dates
- Same global-catalog-vs-per-club-pick split as Movie, but goes one level further: **Series, Season, and Episode are all
  global**, deduplicated by `imdb_id` (Series) / `(series, number)` (Season) / `(season, number)` (Episode) and shared
  by every club following that series. Only the top-level "my club is following this series" fact (`chosen_by`,
  `custom_title`, `display_title_preference`) is per-club — that pick's id is what routes address as the series id;
  Season/Episode ids are the global ones directly, since they have no per-club fields of their own
- Because Episode is global but a meeting is inherently club-specific, episode-to-meeting scheduling is its own join (
  many clubs can each schedule the same global episode to their own different meeting) rather than a field on Episode
- **Ratings are per member per global entity, not per club-pick** — a member has exactly one rating for a given
  series/season/episode regardless of which club they watched it through; any club sharing that entity sees the same
  rating. This is the opposite of Movie's per-pick reviews, deliberately: Movie supports rewatch-and-re-review,
  Series/Season/Episode assume a linear, watched-once progression
- Series cached TMDB metadata: `original_title`, `alternative_titles`, year, genre, `origin_country`,
  `production_countries`, TMDB rating, IMDB rating (via OMDb, see MediaItem above), creator, poster,
  `metadata_fetched_at` — no director/runtime (those are per-episode, not per-series)
- Adding a series through the UI (by IMDB URL or by `tmdb_id` via title search — `SeriesService.addSeries` /
  `addSeriesByTmdbId`) immediately triggers `importSeasonsAndEpisodes` best-effort, instead of leaving Season/Episode
  empty until someone visits `SeasonDetailPage` and adds them one at a time. CSV import already did this explicitly
  (see Existing Data below) — this extends the same behavior to the manual-add path
- Episode cached TMDB metadata: `air_date`, `overview`, `runtime`, director, TMDB rating, `metadata_fetched_at` — no
  title split (the CSV/user-entered `title` is the only one) and no genre/country/creator (those live at the series
  level)
- Episode TMDB lookup is always best-effort: fetched automatically when the parent series has a `tmdb_id`, silently
  skipped otherwise, never blocking episode creation (unlike Movie/Series, an episode has no id of its own to look up
  by)
- CSV import of series sources the **full** Season/Episode catalog from TMDB up front (`SeriesService.importSeasonsAndEpisodes`
  — every season, every episode, including ones the club never watched), then matches each CSV row onto it by
  `(season_number, episode_number)` rather than trusting the CSV's own row structure to define the catalog. A CSV row
  that doesn't match a real TMDB episode/season is a warning, not a silently-misparsed row. If the series can't be
  matched to TMDB at all, import falls back to creating seasons/episodes from the CSV alone (today's original
  behavior, preserved as a fallback, not a regression)

### WatchlistEntry

- Personal per member per Club; visible to all club members
- References a **MediaItem** directly (movie or series) instead of storing its own title/year/rating — added by
  search only, no freeform/manual entry (see MediaItem above)
- Ordered (`position`) within its MediaItem's `type` — the UI shows and reorders Movies and Series as two separate
  lists, so position only needs to be meaningful within one type at a time, not club-wide. Reordering swaps an entry
  with whichever one is immediately adjacent in that type's list. Any club member may reorder it (a shared,
  collaboratively prioritized list); editing an entry's `notes` or deleting it stays owner-only
  (`WatchlistService.requireOwnedEntry`)
- `notes` is the only freeform field left on an entry — personal commentary, not media data

### RatingScale

- Two per Club (quality + sentiment); ordered list of configurable labels
- Seeded with Portuguese defaults at Club creation
- Each option also carries a `color` (hex string, e.g. `#2E7D32`), used for chart/UI display — required, not optional,
  since every option needs one to render consistently. There's no admin API yet to customize scale options (labels or
  colors) after Club creation; `createOption` is only ever called from the default-seeding path.
- Default seeding applies the same six-step green-to-red gradient (
  `#2E7D32, #7CB342, #C0CA33, #FDD835, #FB8C00, #E53935`) by position to both default scales, since both are six options
  ordered best-to-worst

## Schedule Model

- Schedule is pre-generated for a full year at a time (done at year-end for the coming year)
- Each Meeting has an optional `assigned_member` derived from the round-robin rotation
- The rotation order is a simple ordered member list on Club, used only at generation time — not enforced at runtime
- No separate Turn/Slot entity; Meeting is the primary scheduling unit

### Key scenarios

| Scenario                             | Model                                                                    |
|--------------------------------------|--------------------------------------------------------------------------|
| Normal week                          | `Meeting(assigned_member=A)` → one movie `chosen_by=A`                   |
| Multiple movies, one person          | Meeting → two movies both `chosen_by=A`                                  |
| Extra pick by another member         | `Meeting(assigned_member=A)` → movies by A and B                         |
| Merged meeting (two turns, one date) | `Meeting(assigned_member=null)` → movies `chosen_by=A` and `chosen_by=B` |
| Swap                                 | Reassign `assigned_member` on two meetings                               |
| Postpone                             | Change `Meeting.date`                                                    |
| Future empty slot                    | Meeting exists with no movies yet                                        |

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

Sample CSVs in `samples/` (local reference data only — not tracked in git):

- `Movie Club - Movies 2025.csv` / `2026.csv` / `2027.csv` — main schedule and ratings
- `Movie Club - Series.csv` — series episodes with watch dates and ratings
- `Movie Club - Reserve.csv` — per-member watchlist backlog

CSV conventions:

- `Choice` column: member initial (e.g. `A`/`B`) who chose the movie — arbitrary and club-configurable, not hardcoded to
  specific members; mapped to a real member id per import via the `mappings` request field
- Blank `When?` inherits the date from the row above
- `IMDB Id` column: the `tt` identifier used for TMDB lookup
- The importer only reads `Choice`, the rating columns, and `IMDB Id` — descriptive columns (title, year, director,
  duration, genre, country) are ignored; TMDB supplies all of that from the IMDB id via the same best-effort refresh
  used elsewhere
- `Movie Club - Series.csv` has one `IMDB Id` per series (not per season/episode) — it's read from the series header
  row only and drives `SeriesService.importSeasonsAndEpisodes`'s full-catalog TMDB import (see the Series → Season →
  Episode section); the column was originally optional and the parser stayed forward-compatible with files that omit
  it (an absent series id just skips-and-warns that series, same as an unmapped `Choice` initial)
- `Movie Club - Series.csv` rows where `Choice` is `Film` mark a standalone companion movie for the current series (e.g.
  a series finale film) — `SeriesCsvParser` parses them into `SeriesBlock.standaloneFilms`, but the importer ignores
  them entirely (no warning, no row created): they're out of scope for the series importer and belong to the movie
  importer instead
- The import endpoint (`POST /clubs/{clubId}/import`) accepts multiple `file` parts in one multipart request — each is
  imported independently against the same `type`/`mappings`, and the resulting `ImportResult`s are merged (counts
  summed, `skipped`/`warnings` concatenated)
