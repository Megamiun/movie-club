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
- Has `preferred_languages` (ordered ISO 639-1 codes, ranked) and `ignored_languages` (unordered) — used to resolve a
  display title for any Movie/Series pick that's left at the default `ORIGINAL` preference (see Movie below); both
  default to empty, admin-only to edit (`PATCH /clubs/{clubId}/language-preferences`). `LanguagePreferencesSection`
  sends this PATCH immediately after every add/remove/reorder (no batching "Save" button) — same immediate-call,
  revert-local-state-on-error pattern already used by the member-color editor, applied here via a shared `persist`
  helper since one PATCH always carries both lists together

### Member

- Authenticates via email/password; accounts created only through invite tokens
- Belongs to one or more Clubs with a role
- Has a personal **Watchlist** per Club (visible to all club members)
- Has a `color` (hex string) per club membership — on `club_members`, not on Member itself, since it's a per-club
  identity, not a global one (deliberately mirrors rating options' own per-club `color`). Auto-assigned from an
  8-color palette by rotation order when a member joins (`ClubService.MEMBER_COLOR_PALETTE`), then freely editable
  after that — self-service by the member themselves, or by any club admin (`PATCH
  /clubs/{clubId}/members/{memberId}/color`). Used for the meetings table's chosen-by column (`MemberBadge`, a
  colored initials avatar) instead of the plain member name
- Has `is_site_admin` (default false) — a member-level flag, unlike every other role/permission in this app, which
  is scoped per-club (`club_members.role`). No self-service way to grant it exists yet (no UI, no endpoint) — it's
  bootstrapped onto whichever account has the earliest `created_at` by a migration (V23), on the assumption that's
  the operator who originally stood up the instance. `AdminService.requireSiteAdmin` gates the `/admin/*` routes;
  every other `*Service` in this codebase scopes access by club membership/role instead, so this is the one
  deliberate exception
- **Site Admin** panel (`/admin`, `AdminPage`, site-admin members only — the nav link itself is hidden for everyone
  else, though the backend is the actual enforcement, not the hidden link): lists every registered user, and every
  `MediaItem` site-wide (movies + series pulled from every club at once) — read-only for now, no user/content
  management actions. Reuses the existing `MediaItem` "universal handle" table wholesale (`MediaItemRepository.listAll`)
  rather than separately unioning the `Movies`/`Series` catalog tables, since MediaItem already dedupes both types
  into one row per TMDB item

### Meeting

- An event with a date
- Optional `assigned_member` (whose round-robin slot it is; null if shared/merged)
- Owns movies and series episodes
- Meetings can be merged (move movies, delete empty one), postponed (change date), or split

### MediaItem

- Universal handle for anything sourced from TMDB — `type` (MOVIE|SERIES|EPISODE; EPISODE is reserved but not
  populated yet, see below), deduplicated by `imdb_id`, plus `tmdb_id`, `title`, `year`, `poster_url` (an external
  TMDB CDN URL string, unrelated to Movie/Series' own long-unused `poster_s3_key`), and IMDB rating
- Created *only* by a successful TMDB lookup (search result, or an IMDB id/URL resolved through TMDB) — never from
  freeform/manually-typed input. This is a deliberate policy, not just today's implementation default: nothing
  should ever reference a MediaItem that TMDB couldn't derive
- Movie and Series catalog rows each carry a `media_item_id` pointing at their MediaItem, created/refreshed
  alongside their own richer columns (director/runtime for Movie, creator for Series — those stay on Movie/Series
  themselves; MediaItem isn't a replacement for the full catalog row, just a shared cross-type reference point)
- Episode still doesn't have a `media_item_id` — deliberately, not just deferred: nothing reads it, since Watchlist
  (MediaItem's one cross-type consumer) doesn't support Episode entries. Episode's own `imdb_id` is fetched (via
  `append_to_response=external_ids` on the same per-episode TMDB call, see the Series → Season → Episode section
  below) and stored directly as a plain column on `episodes`, the same way Movie/Series already carry their own
  `imdb_id` alongside their `media_item_id` — MediaItem would just be unused indirection here until something
  besides display (i.e. Watchlist) actually needs to reference an Episode across types
- WatchlistEntry references a MediaItem directly instead of duplicating title/year/rating itself (see below)
- IMDB's own rating is fetched separately from OMDb (`OmdbClient`, `OMDB_API_KEY`) since TMDB's API never exposes it
  (only its own `vote_average`) — optional, silently no-ops when the key is unset, never blocks an add/refresh. The
  "never blocks" guarantee lives inside `OmdbClient.getImdbRating` itself (a `runCatching` around the request,
  returning `null` on any failure) rather than in each caller — none of Movie/Series/Watchlist/EpisodeService wrap
  the call themselves, so the client has to be the one place this is actually enforced.
  IMDB rating is the *only* rating source anywhere in the app — TMDB's `vote_average`/`tmdb_rating` was fully
  removed (schema, backend, UI) rather than kept as a fallback, so the UI never needs to label a rating's source
  (no "IMDB"/"TMDB" prefix, just the bare number, e.g. `8.7`) since there's only ever one possible source. A row
  added before OMDb was wired in, or where OMDb had no match, simply shows no rating

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
- Cached TMDB metadata (on the catalog row): `original_title`, `original_language` (ISO 639-1), `translations` (TMDB's
  per-language `/translations` endpoint, fetched via `append_to_response` — each entry has a language code, country
  code, TMDB's English name for that language, and the translated title; entries with no title override are dropped,
  see `TmdbTranslations.toTranslations`. Replaces the old per-country `alternative_titles`, which was keyed the wrong
  way for language-based resolution and is gone entirely, not kept alongside), year, director, runtime, genre,
  `origin_country`, `production_countries` (a second, distinct country list — TMDB's full production-country objects,
  not just origin codes), IMDB rating (via OMDb, see MediaItem above — TMDB's own `vote_average`/`tmdb_rating` was
  removed entirely, see MediaItem above), poster (stored in S3), `metadata_fetched_at`. Also `director_imdb_id` — resolved from the credited director's TMDB *person* id (`credits`
  crew entry, job `"Director"`) via a second best-effort `/person/{id}/external_ids` call
  (`TmdbClient.getPersonExternalIds`); like the OMDb rating lookup, a failure here (rate limit, no linked IMDB page)
  never blocks adding/refreshing the movie itself, it just leaves `director_imdb_id` null. Used to link the
  director's name to their own IMDB page, separate from the movie's own `imdb_id`/`ImdbLink`
  (`kind="name"` vs the default `kind="title"`)
  `display_title_preference` (ORIGINAL|CUSTOM|LANGUAGE, default ORIGINAL) lives on the pick row, alongside
  `display_language_code` (set only when preference is LANGUAGE — a language icon on the movie/series detail view
  opens a dialog listing that item's `translations` to pick from). Resolving ORIGINAL/CUSTOM/LANGUAGE into an actual
  display string is a pure client-side function (`resolveTitle` in `frontend/src/utils/title.ts`), not done
  server-side — it only needs data already in the API response (the pick's own fields + the club's language lists),
  so there was no reason to thread it through every read path in `MovieService`/`SeriesService`. Algorithm: LANGUAGE
  wins if set and a matching translation exists; else try the club's `preferred_languages` in rank order (skipping any
  that are also `ignored_languages`), first match wins; else fall back to the original title, unless the original's
  own language is itself ignored, in which case fall back to any non-ignored translation before finally giving up and
  showing the original title anyway. (ENGLISH used to be a third preference value but was dropped — it was never
  actually resolved anywhere server-side or client-side; LANGUAGE + `display_language_code` is a strict superset)
- Metadata can be manually refreshed (for unreleased films with missing fields) — refreshing from any one pick updates
  the shared catalog row for every other pick of the same movie
- Separate "where to watch" link (e.g. HBO, Netflix, magnet link) — per-meeting-pick, not global
- Per-member **ratings**: quality scale + sentiment scale (both optional) — keyed to the per-meeting pick, not the
  global movie, since the same movie rewatched at a later meeting can get a fresh rating
- Per-member **comments** (free text, optional)
- Deleting a pick removes only that meeting's choice; the shared catalog row (and any other club's pick of it) is
  untouched
- A pick can be moved to a different meeting (`POST /movies/{movieId}/move`, `MovieService.moveToMeeting`) without
  losing its reviews/custom title/watch link — it repoints the *same* `MeetingMovies` row's `meeting_id` rather than
  deleting and re-adding, reusing the same `MovieRepository.updateMeeting` primitive `MeetingService.mergeMeetings`
  already used for merging a whole meeting's movies at once. Both meetings must belong to the same club, and the
  movie can't already be picked at the target. The meetings table (`MeetingsPage`) exposes this as native HTML5
  drag-and-drop — dragging a movie/episode row and dropping it anywhere within a different meeting's block
- A club-level "Movies" tab (`MoviesPage`) lets a member search TMDB independent of any specific meeting, then add
  the result straight to a chosen meeting or to their watchlist — a thin UI composing `movieService.searchMovies`,
  `addMovieByTmdbId`, and `WatchlistService.addEntry`, no new backend endpoint

### Series → Season → Episode

- Parallel side track; runs alongside movies, can share the same meeting dates
- Same global-catalog-vs-per-club-pick split as Movie, but goes one level further: **Series, Season, and Episode are all
  global**, deduplicated by `imdb_id` (Series) / `(series, number)` (Season) / `(season, number)` (Episode) and shared
  by every club following that series. Only the top-level "my club is following this series" fact (`chosen_by`,
  `custom_title`, `display_title_preference`) is per-club — that pick's id is what routes address as the series id;
  Season/Episode ids are the global ones directly, since they have no per-club fields of their own
- Because Episode is global but a meeting is inherently club-specific, episode-to-meeting scheduling is its own join (
  many clubs can each schedule the same global episode to their own different meeting) rather than a field on Episode.
  Moving an episode to a different meeting (drag-and-drop on the meetings table, same as Movie above) has no
  dedicated "move" endpoint or service method — the join row has no fields of its own to preserve, so the frontend
  just composes the existing `unassignFromMeeting` + `assignToMeeting` calls
- **Ratings are per member per global entity, not per club-pick** — a member has exactly one rating for a given
  series/season/episode regardless of which club they watched it through; any club sharing that entity sees the same
  rating. This is the opposite of Movie's per-pick reviews, deliberately: Movie supports rewatch-and-re-review,
  Series/Season/Episode assume a linear, watched-once progression
- Series cached TMDB metadata: `original_title`, `original_language`, `translations`, year, genre, `origin_country`,
  `production_countries`, IMDB rating (via OMDb, see MediaItem above), creator, poster,
  `metadata_fetched_at` — no director/runtime (those are per-episode, not per-series). Same `translations`/
  `display_title_preference`/`display_language_code`/client-side resolution as Movie (see above) — `ClubSeries` has
  its own `display_language_code` column, separate from `MeetingMovies`'
- Adding a series through the UI (by IMDB URL or by `tmdb_id` via title search — `SeriesService.addSeries` /
  `addSeriesByTmdbId`) immediately triggers `importSeasonsAndEpisodes` best-effort, instead of leaving Season/Episode
  empty until someone visits `SeasonDetailPage` and adds them one at a time. CSV import already did this explicitly
  (see Existing Data below) — this extends the same behavior to the manual-add path. Since that initial trigger is
  wrapped in `runCatching` (a TMDB hiccup shouldn't fail the add itself), a transient failure partway through (rate
  limit, timeout) can leave the catalog permanently incomplete with no error surfaced — `importSeasonsAndEpisodes`
  is idempotent and re-runnable, but nothing called it again automatically. `POST /series/{seriesId}/import-seasons`
  exposes it directly (a "Refresh seasons/episodes" button on `SeriesDetailPage`) as the recovery path — re-running
  only fills in what's missing, never duplicates what's already there
- Episode cached TMDB metadata: `air_date`, `overview`, `runtime`, director, `director_imdb_id` (same best-effort
  TMDB-person-id → IMDB-id resolution as Movie, see above), `imdb_id` (the episode's own, from TMDB's per-episode
  `external_ids` — requested via `append_to_response` on the same call as the rest of an episode's metadata, unlike
  `director_imdb_id` which needs its own separate `/person/{id}/external_ids` round trip), `metadata_fetched_at`, and
  now also its own `imdb_rating` — fetched from OMDb (see MediaItem above) using the episode's own `imdb_id` the
  moment `refreshMetadata` resolves it, same best-effort/never-blocks pattern as Movie/Series. Null until a refresh
  has run (same as `imdb_id` itself); the meetings table prefers the episode's own rating and falls back to the
  parent series' rating only when the episode doesn't have one yet. No title split (the CSV/user-entered `title` is
  the only one) and no
  genre/country/creator (those live at the series level). `imdb_id` is null until something actually calls
  `EpisodeService.refreshMetadata` on that episode — the bulk `/tv/{id}/season/{n}` catalog import
  (`SeriesService.importSeasonsAndEpisodes`) doesn't include per-episode external ids, so a freshly-imported
  episode has no IMDB link yet
- Episode TMDB lookup is always best-effort: fetched automatically when the parent series has a `tmdb_id`, silently
  skipped otherwise, never blocking episode creation (unlike Movie/Series, an episode has no id of its own to look up
  by)
- Every episode listing in the UI prefixes the episode with its "S#E#" code. `Episode` itself only carries
  `seasonId`, not the parent season's own `number`, so this is resolved via `GET /seasons/{seasonId}`
  (`SeasonService.getById`) — same club-membership-derived permission check as `SeasonService.rate`. Pages that
  already know a single fixed season (`SeasonDetailPage`) fetch it directly; pages that can show episodes spanning
  several seasons/series at once (`MeetingsPage`, the meeting detail page's Episodes section) use a shared
  `useSeasonNumbers` hook that resolves every distinct `seasonId` on the page in parallel into one lookup map. The
  shared `episodeCode()` util (`frontend/src/utils/episode.ts`) falls back to just "E#" while the season number is
  still loading, rather than blocking the row on it
  - Both halves zero-pad to the width of the largest number they could show (e.g. `S0E001` in a 130-episode
    season) — `useSeasonNumbers` also resolves, per distinct `seasonId`, the season's siblings (via
    `GET /seasons/{seasonId}/siblings`, `SeasonService.listSiblingSeasons` — every season sharing that season's
    parent series, resolved straight from the season the same way `getById` derives access, without the caller
    needing the club-scoped series pick id `listSeasons` requires) for the season-number digit width, and the
    season's own episode list for the episode-number digit width (scoped to *that* season, not the whole series).
    `SeasonDetailPage` derives both directly from data it already loads (its own `listSiblings` call plus the full
    episode list already shown on the page) rather than going through the shared hook. `episodeCode()` falls back
    to unpadded numbers when a width isn't known yet, same "never block the row" principle as the season-number
    fallback above
- When assigning an episode to a meeting, the UI suggests one "up next" episode per series the club follows —
  the earliest (season, then episode number) episode of that series not yet scheduled to any of the club's own
  meetings (`GET /clubs/{clubId}/episodes/next-suggestions`, `EpisodeRepository.findNextUnscheduled`). Series with
  nothing left to suggest (fully scheduled, or no episodes imported yet) are silently omitted, not an empty/error
  state. Scoped per club: another club scheduling the same global episode doesn't affect this club's own suggestion.
  A series is also skipped once it's no longer running (TMDB's own `status` field, e.g. "Ended"/"Canceled" — new
  `series.status` column, refreshed the same way as the rest of a series' cached TMDB metadata) *unless* the club is
  actively watchlisting it (`WatchlistRepository.existsByClubAndMediaItemImdbId`, keyed by `imdb_id` rather than a
  `mediaItemId` since `SeriesRow` doesn't carry one) — an ended show isn't worth nudging towards continuing on its
  own, but a club that's deliberately queued it up to catch up on clearly still wants the nudge. A series with no
  `status` yet (not refreshed since this field was added) is treated as still running, not filtered out
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
  (`WatchlistService.requireOwnedEntry`). The frontend offers both up/down icon buttons and drag-and-drop (`@dnd-kit`,
  `WatchlistPage`'s `DndContext`/`SortableContext`) for reordering — dragging further than one slot just replays the
  same adjacent-swap `POST /watchlist/{id}/move` call once per step to walk the entry to its dropped position,
  reusing the existing primitive rather than adding a "set exact position" endpoint
- `notes` is the only freeform field left on an entry — personal commentary, not media data
- Movies only (not Series) can be moved between a meeting pick and the watchlist, in either direction — there's no
  dedicated backend "move" endpoint; the frontend just composes the existing add + delete calls (e.g. add the movie
  to the meeting via its `imdb_id`, then delete the watchlist entry only once that succeeds, so a rejected add
  — e.g. "already added to this meeting" — leaves the watchlist entry untouched instead of losing it). Owner-only
  in the watchlist-to-meeting direction, same as editing/deleting an entry

### RatingScale

- Two per Club (quality + sentiment); ordered list of configurable labels
- Seeded with Portuguese defaults at Club creation
- Each option also carries a `color` (hex string, e.g. `#2E7D32`), used for chart/UI display — required, not optional,
  since every option needs one to render consistently. Admins can rename/recolor/reorder/add/remove options after
  Club creation (`PATCH /clubs/{clubId}/rating-options/{optionId}`, `PUT .../rating-scales/{scaleId}/order`,
  `POST .../rating-scales/{scaleId}/options`, `DELETE .../rating-options/{optionId}?reassignToOptionId=...`).
  Deleting an option requires naming another option in the *same* scale to take over any reviews already using it
  (`ClubService.deleteRatingOption` fans out to `reassignRatingOption` on `MovieRepository`/`SeriesRepository`/
  `SeasonRepository`/`EpisodeRepository` before deleting the option row itself — repositories still don't depend on
  each other, so this cross-entity composition lives in the service, same as `SeriesService`'s own multi-repository
  work) — the last remaining option in a scale can't be deleted, since there'd be nothing to reassign to. After
  deleting, the survivors' `position` values are immediately renumbered to stay contiguous `0..N-1` via a shared
  private `assignContiguousPositions` helper (also used by `updateRatingOptionOrder`, which does the same
  "these ids, in this order, become positions `0..N-1`" assignment from a caller-supplied order instead of the
  current DB order) — every other consumer of `position` (rank display in `InlineRatingEditor`'s `rankOf`,
  `createRatingOption`'s next-position-is-`size` calculation) assumes no gaps, so this can't be deferred to
  "whenever the next reorder happens to fix it"
- Every place a user picks a color by hand (a rating option's color, a new option's initial color, a member's own
  color) uses a shared `PastelColorPicker` (`frontend/src/components/PastelColorPicker.tsx`) instead of a native
  `<input type="color">` — a hue-only slider at a fixed pastel saturation/lightness (`frontend/src/utils/
  pastelColor.ts`), so every color it can produce is pastel by construction rather than relying on the user staying
  within some band. Storage is still a plain hex string, unchanged. This only constrains user *choices* going
  forward — the seeded default palettes above are deliberately not all-pastel (quality mixes pastel and solid) and
  aren't retroactively touched; a pre-existing non-pastel color is left alone until the member/admin actually
  drags the picker themselves, at which point the thumb starts at that color's closest hue on the pastel band
- Default seeding gives each scale its *own* six-color best-to-worst palette, matching the original spreadsheet's own
  chip colors per option (see `samples/img_1.png` for quality, `img_2.png` for sentiment) rather than a single shared
  gradient reused across both scales — quality mixes pastel and solid chips, sentiment is consistently pastel;
  quality's positive end is green, sentiment's is blue. `V17`/`V18__rating_scale_default_colors*.sql` retrofit
  existing clubs' still-default-colored options to this palette (each matched by exact old label+color together, so
  a club that already customized a color is untouched). Since chips range from pale to solid, the frontend picks
  white-vs-dark chip text per option by luminance (`frontend/src/utils/color.ts`) rather than assuming either
- The Meetings table's per-member rating box (`InlineRatingEditor`) is driven by two personal display settings —
  gradient blend percent (0–50%, how much of the box blends between the quality and sentiment colors around the
  midpoint) and fill content (`Number` = rank digit, `Description` = written label, `No text` = color only, no
  label) — held in `frontend/src/settings/RatingDisplayContext.tsx` and persisted to `localStorage`, not on Member
  server-side, since it's a personal display preference like a theme toggle rather than club data. Edited via a
  Tune-icon button next to the Meetings page heading. A rating that hasn't been given renders with no fill color at
  all (fully transparent, no text regardless of the fill-content setting) — never the other rating filling the whole
  box — so fill color only ever represents a rating that was actually given; the blend band itself is only ever
  drawn when both quality and sentiment are set. The box's own dashed outline (in the member's own club color) is
  always shown regardless, though, even for a fully unrated cell — it's the click target, not a rating indicator,
  so there's still something to click even when nothing's been rated yet
- Saving a rating in the Meetings table, and the table more generally, doesn't reload-and-flash the whole page.
  `useAsync` exposes a `silentReload` alongside `reload` — same refetch, but never sets `loading`, so the
  `AsyncState` wrapper never unmounts the table for it (no spinner, no lost scroll position, no closed popovers).
  `MeetingsPage` uses it both as the `onChange` passed down into every pick row (so any mutation — a rating save,
  a delete, a drag-and-drop move — patches state in place) and on a 5-second `setInterval`, so other members'
  concurrent changes show up without a manual refresh. A failed background poll is silently dropped rather than
  surfaced, since whatever's already on screen is still valid. `ClubOutletContext` (the club-detail fetch every
  club-scoped page shares) exposes the same `silentReload` alongside `reload` — `ClubOverviewPage`'s member-color
  editor uses it specifically (a per-drag-commit save that shouldn't flash the entire Overview page — tabs, every
  other section — back to a spinner), while genuinely structural member changes (add/remove/role-change) still use
  the full `reload`, since those actually add/remove table rows
- Light/dark theme: `theme.ts` already declared `colorSchemes: { light: true, dark: true }` (MUI's CSS-vars mode)
  plus `defaultColorScheme: 'light'` so a fresh visitor always starts light rather than following OS preference. A
  sun/moon `IconButton` in `AppLayout`'s nav bar calls MUI's own `useColorScheme().setMode(...)`, toggling directly
  between `'light'`/`'dark'` (no third "system" state, to keep it a simple binary toggle matching the app's other
  personal-preference toggles) — MUI persists the chosen mode to `localStorage` itself, no app code needed for that
  part. No component-level dark-mode-specific styling was needed elsewhere; the app already used theme-relative
  color tokens (`text.secondary`, `Paper`, etc.) throughout rather than hardcoded hex values

## Schedule Model

- Schedule is pre-generated for a full year at a time (done at year-end for the coming year)
- Each Meeting has an optional `assigned_member` derived from the round-robin rotation
- The rotation order is a simple ordered member list on Club, used only at generation time — not enforced at runtime.
  `RotationSection` (`ClubOverviewPage`) sends `PUT /clubs/{clubId}/rotation` immediately after each reorder (no
  batching "Save" button), reverting the local order on a failed save — same pattern as the member-color editor.
  Both this and `LanguagePreferencesSection`'s equivalent immediate-save (see Club above) read/write their local
  state through React's functional `setState` updater form rather than a closed-over variable — two rapid actions
  (e.g. clicking "move up" twice, or removing two chips) can both fire from the same render before React
  re-renders in between, and a plain closure read in that window would have the second action compute from the
  same stale array the first one saw, silently dropping whichever save resolves last
- No separate Turn/Slot entity; Meeting is the primary scheduling unit
- The Meetings page groups meetings into year tabs (client-side, derived from each `Meeting.date` — no `year` field
  or endpoint of its own), matching how the schedule itself is generated a year at a time. Sorted newest-first
  (leftmost tab = latest year). Defaults to the current calendar year, or the most recent year with any meetings if
  the current year has none yet. Landing on (not just switching to) the current-year tab auto-scrolls the earliest
  meeting dated today-or-later into view (`block: 'center'`) — the latest meeting instead if every meeting that
  year is already in the past. Scrolls once per year selection (a `useRef` guard, not a data-driven effect), so the
  5-second background poll refreshing meeting data doesn't keep re-scrolling the page out from under the reader

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
