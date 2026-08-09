# TODO

- [x] Allow user to be invited by email
- [x] Add a unique username to user
- [x] On import, show an autocomplete dropdown of available members instead of member id
- [x] Check what are the notes in the watchlist
- [x] Use my token for the docker compose too
- [x] Add a series and movies search on the site
- [x] On creating meeting, add an autocomplete dropdown for members of the club
- [x] Member ids should not be visible usually, use names
- [x] Add member to club should search by name or email instead of raw member id
- [x] Allow editing scale text, colors, and order easily
  - [ ] Allow removing and adding new ratings to the scale, if removing, need to choose which one will receive the old rating
- [x] Club start screen should be a list of next meetings; configs and club data should be last tab
- [x] Watchlist "add episode" and "add movie" on the UI should use search by name from TMDB, or IMDB URL/ID
- [x] Admin panel to see all imported series, movies, and all users
  - Site-wide (confirmed with the user, not per-club) -- new `is_site_admin` flag on Member (per-member, unlike every other role in this app which is per-club), bootstrapped onto the earliest-created account by a migration (V23) since no self-service grant flow exists. New `AdminService` + `GET /admin/users` + `GET /admin/media-items`, gated by `requireSiteAdmin`. New `/admin` page (nav link hidden for non-admins, though the backend is the real enforcement) with two read-only tables: every registered user, and every `MediaItem` site-wide across all clubs (reuses the existing MediaItem table instead of separately unioning Movies/Series)
- [x] When adding a movie/series/etc. that already exists, update the existing record with new/missing info instead of duplicating
  - Movie/Series catalog rows already dedupe-and-refresh by `imdb_id` on add (`findOrCreateMovie`/`findOrCreateSeries`); the shared `MediaItem` table (see CLAUDE.md) extends this same dedup-by-`imdb_id` behavior to anything referencing it (e.g. Watchlist)
- [x] Don't add repeated entries to meetings, watchlist, series and so on.
  - `MovieService`/`SeriesService`/`WatchlistService`/`EpisodeService` now reject a second add of the same `imdb_id`/MediaItem/episode within its natural scope: same meeting for a movie pick (rewatch at a *different* meeting is still allowed, by design), same club for a series pick, same (club, member) for a watchlist entry, same meeting for an episode assignment -- all four now give the same clear error instead of three erroring and one silently no-opping. Backed by real DB unique constraints (`club_series`, `meeting_movies`, `watchlist_entries` -- `meeting_episodes` already had one) so a race between two near-simultaneous requests can't slip a duplicate past the application-level check either. CSV import keeps its own separate, softer "already imported" skip logic (a warning, not a hard error), since re-running an import is expected to encounter rows it's seen before
- [x] Watchlist should be positional (orderable, positions can change)
  - Position scoped per (club, MediaItem type); reorder via up/down arrows swapping with the adjacent same-type entry
- [x] Watchlist ordering should be able to be done also via drag and drop
  - New dependency: `@dnd-kit/core` + `@dnd-kit/sortable` + `@dnd-kit/utilities` (actively maintained, accessible; `react-beautiful-dnd` is not). Dragging replays the existing adjacent-swap `move` endpoint once per position stepped, so no backend change was needed. Up/down arrow buttons kept alongside as a non-drag fallback
- [x] Watchlist entries can show media details (year, rating, etc. from TMDB)
  - Entries reference a `MediaItem` directly (poster, title, year, IMDB/TMDB rating); adding is search-only (no freeform manual entry), since a MediaItem only ever exists from a successful TMDB lookup
- [x] Meetings pane should show film data similar to the CSV file (director, runtime, genre, etc.)
  - Movie pick metadata line now also shows Country (was already showing director/runtime/genre); Episode gained a Director/Runtime line and a TMDB rating chip (had neither before)
  - The Meetings *list* tab itself was showing nothing about picks at all (just date + assigned member) -- `GET /clubs/{clubId}/meetings` and `GET /meetings/{id}` now return each meeting composed with its `movies`/`episodes` (`MeetingService.getMeeting`/`listMeetings` return a new `MeetingWithPicks`, reusing the existing `MovieRow.toResponse()`/`EpisodeRow.toResponse()` mappers instead of duplicating them), so the list shows each meeting's picked titles (or "Nothing picked yet") without opening every meeting individually
  - Rendered as one continuous table (no header row, no wasted leading columns for pick rows — only the date/assigned meta row spans the full width) so movie and episode rows visually align like a spreadsheet, matching the original CSV layout. Each pick row shows every club member's own quality + sentiment rating (`InlineRatingEditor`), click-to-edit only for the viewer's own — `MeetingMoviePick`/`MeetingEpisodePick` carry the full review list (`MovieRepository`/`EpisodeRepository.listReviews`), not just the acting member's, since reviews are already club-visible elsewhere. (Originally one column per member, each showing two separately-colored chips — since replaced by a compact pill next to the title, see below)
  - Default rating-scale colors now match the original spreadsheet's own per-option palette (`samples/img_1.png`/`img_2.png`) instead of one shared gradient reused across both scales — see RatingScale in the Domain Model section. Sentiment is consistently pastel and quality mixes pastel/solid, so chip text color is now picked per-chip by luminance (`frontend/src/utils/color.ts`) instead of assumed white
  - Leftmost column shows who chose each pick (`movie.chosenById`, resolved to a name) — episodes don't have a "chosen by" concept yet (no `chosen_by` on `meeting_episodes`), shown as `—`
  - Episode rows inherit any field they don't have their own value for from the parent series -- chosen-by, IMDB link, genre, and country (all previously "—" on every episode row, since those are series-level facts an Episode doesn't itself carry), plus year/director falling back to the series' year/creator when the episode's own `air_date`/`director` is unset. A lightweight series-title line still groups episodes by series (shown once before that series' first episode in a meeting), but carries no data of its own now that each row is self-sufficient. `MeetingEpisodePick.series` is the club's own full pick (`SeriesRow`), resolved via `EpisodeRepository.findSeriesImdbId` + `SeriesRepository.findByClubAndImdbId` -- kept as two repository calls composed in `MeetingService` rather than one cross-repository lookup, per the "repositories never depend on other repositories" rule
- [x] Everywhere a movie/series title is shown, add a clickable IMDB icon linking to its IMDB page
  - New shared `ImdbLink` component (also used to de-duplicate Watchlist's inline version); added to meeting movie picks, series list, and series detail. Episodes didn't have their own `imdb_id` at the time, so were skipped -- since fixed, see below
- [x] Add a TMDB attribution notice ("This product uses the TMDB API but is not endorsed or certified by TMDB")
  - Site-wide footer in `AppLayout`
- [x] Separate Series and Movies watchlist in the UI
- [x] Add a movie tab where you can search for movies outside a meeting, with a button to add to a meeting or to the wishlist
  - New "Movies" club tab (`MoviesPage`, between Meetings and Series). TMDB search + a meeting picker + "Add to meeting"/"Add to watchlist" buttons, composing the existing search/add endpoints -- no new backend endpoint
- [x] When adding a series episode to a meeting, try to suggest next episode of current series
  - New `GET /clubs/{clubId}/episodes/next-suggestions` (`EpisodeRepository.findNextUnscheduled`) returns one suggestion per followed series -- its earliest episode not yet scheduled to any of the club's meetings, skipping series with nothing left to suggest. Shown as clickable "Up next" chips in the meeting detail page's Episodes section, alongside the existing search box
  - [ ] Only show suggestions from already running series, or from series in our watchlist
- [x] Allow Club to have a list of ranked preferred languages
- [x] Allow Club to have a list of ignored languages
- [x] Instead of keeping alternative titles, use translations field, which calls translations API
  - `Movie`/`Series` now store `translations` (TMDB's per-language `/translations` endpoint, via `append_to_response`) instead of `alternative_titles` (per-country); also added `originalLanguage`
- [ ] Languages updates should be sent to the BE as soon as changed in the FE
- [ ] Rotation order should be sent to the BE as soon as changed in the FE
- [x] Allow Clubs to default to:
  - [x] first available preferred language, or original if unavailable
  - [x] use original title, unless in ignored languages
  - Resolution (`resolveTitle` in `frontend/src/utils/title.ts`) runs client-side, not server-side — it's a pure display concern that only depends on data already in the API response (translations + club's language lists), so it didn't need threading through every read path in `MovieService`/`SeriesService`
- [x] Allow Clubs to change the exhibition language for a certain media
  - [x] do that by clicking an language icon in the details page, where you will open a dialog, where you may select any translation
  - `DisplayTitlePreference` is now `ORIGINAL | CUSTOM | LANGUAGE` (dropped `ENGLISH`, which was never actually resolved anywhere — `LANGUAGE` + a per-pick `displayLanguageCode` is a strict superset); `LanguagePickerDialog` component reused by both `MovieSection` and `SeriesDetailPage`
- [x] Add Move to Watchlist button in meeting
- [x] Add move to meeting button in Watchlist
  - Scoped to movies only (not series) -- my own reasonable-default decision, not explicitly requested. `MovieSection`'s movie accordion gained a bookmark icon that adds the movie to the acting member's watchlist then deletes the meeting pick; each movie watchlist entry gained a meeting picker + move icon (owner-only) that does the reverse. No new backend endpoint -- both directions just compose the existing add/remove calls, delete-after-successful-add so a rejected add (e.g. "already in this meeting") doesn't lose the watchlist entry
- [x] Allow taking movies/episodes from one meeting to another via drag and drop
  - Native HTML5 drag-and-drop (not `@dnd-kit`, which doesn't play well with `<table>`/`<tr>` layout) -- drag a movie/episode row, drop it anywhere within a different meeting's block. New `POST /movies/{movieId}/move` + `MovieService.moveToMeeting` repoints the existing pick row (keeps reviews/custom title/watch link) rather than delete-and-re-add; episodes reuse the existing assign/unassign endpoints since the join row has no fields to preserve. Both directions reject moving between different clubs' meetings or onto a meeting that already has that movie/episode
- [x] Give each member a color inside the club(editable)
- [x] Use this color and initials in the first column of the meetings table
  - `color` on `club_members` (V20 migration, backfilled for existing members by rotation order), auto-assigned from an 8-color palette when a member joins. Editable by the member themselves or any club admin (color swatch in the club Overview page's Members table). Meetings table's chosen-by column now shows a `MemberBadge` (colored initials avatar) instead of the plain name
- [x] Use flag instead of country name(Show name over hover)
  - Meetings list only (that's where country was shown). Emoji flag derived from `originCountry` ISO codes (regional-indicator-symbol trick, `frontend/src/utils/country.ts`) instead of the `productionCountries` name list; each flag has a `Tooltip` with the full name via `Intl.DisplayNames`
- [x] Totally delete TMDB rating field, only show IMDB
  - [x] No need to show IMDB in table
  - Removed `tmdb_rating` from the DB schema (`movies`/`series`/`episodes`/`media_items`, V21 migration), backend DTOs/services/API responses, and the frontend everywhere (Meetings table, movie/series detail pages, watchlist). Also dropped the now-fully-unused `TmdbClient.toRatingScale()`/`vote_average` plumbing that only ever fed it. Since IMDB is now the sole rating source, `ratingLabel` just returns the bare number with no "IMDB"/"TMDB" prefix. Episode never had its own OMDb-fetched IMDB rating (only TMDB's, now gone) — meetings table episode rows fall back to the parent series' rating instead, same as before
- [x] Make sure series/seasons/episodes are always linked to imdb items
  - Series already was (its own dedup key). Season has no IMDB-page concept in TMDB's API at all, so nothing to link. Episode was the real gap: TMDB's per-episode `external_ids` (`imdb_id`) is now requested via `append_to_response` on the same call as the rest of an episode's metadata (V22 migration adds `episodes.imdb_id`), and used for a new `ImdbLink` on the episode accordion in both the meeting detail page and the season detail page, plus the meetings table's episode title link (which now prefers the episode's own id over the parent series'). Deliberately stored as a plain column rather than via `MediaItem` -- Watchlist is the only thing MediaItem exists to serve across types, and it doesn't support episodes, so the indirection would be unused. Best-effort like the rest of episode enrichment: null until `refreshMetadata` is called, since the bulk season-import endpoint doesn't include it
- [x] Put ratings just after name of the series, use smaller font, make their size constant
- [x] Extra points: Try to join together both ratings in a single item, with a gradient from one color to the other
  - If too hard of bad for reading, do an ellipse with the one half each color.
  - Went straight for the half-and-half split (the explicitly-sanctioned fallback) rather than a true blended gradient, since a soft blend between two arbitrary hex colors (especially pastel-into-pastel) reads muddy. `InlineRatingEditor` now renders one small (16px) fixed-size circular dot instead of two variable-width labeled chips -- solid if only one of quality/sentiment is set, split via a hard-stop `linear-gradient` if both are, dashed-outline placeholder if editable and empty. No label text on the dot itself (that's what kept the old chips from being constant-size); full labels + member name surface via a `Tooltip` on hover, and click-to-edit opens the same Select popover as before
  - Follow-up (after trying it live): moving every member's dot into the title cell made "who rated what" unclear, so this was walked back to one column per member again (see below) -- the compact-dot *style* survives, just not the everything-in-the-title-cell placement
- [x] Change from link icon to underlined in meetings list
  - `ImdbLink` gained a `variant: 'icon' | 'text'` prop; meetings list uses `'text'`, everywhere else keeps the icon
  - Follow-up: the movie/episode title itself is now the underlined link (was a plain title + separate "IMDB" text before)
- [x] Limit genres to 2, followed by a "+ x", all are shown if hovered
  - New `TruncatedList` component (`frontend/src/components/TruncatedList.tsx`), meetings list only for now
  - [ ] For genres, limit total length, if second genre is too long, also don't show it, should always fit in one line
- [x] Convert duration to format like '1h25m' or '45m'
  - `frontend/src/utils/duration.ts`, meetings list only for now
  - [ ] Align to the right
  - [ ] Fill the minutes with spaces(or 0) if the minutes part is just one character
- [x] Only show year on meetings page, but allow to hover to see whole date
  - Clarified: this was about episode air dates, not the meeting's own date. Episode rows show just the year (from `air_date` or the series' `year` as fallback), with the full `air_date` as a hover tooltip when known
- [x] Director should also link to imdb director page
  - [x] Also save them to DB, get from tmdb, save imdb id
  - `director_imdb_id` column on `movies`/`episodes` (V19 migration), resolved best-effort from the credited director's TMDB person id via `/person/{id}/external_ids` (never blocks add/refresh on failure, same as the OMDb rating lookup). `ImdbLink` gained a `kind: 'title' | 'name'` prop for linking to a person's IMDB page instead of a title's. Meetings list only for now, existing picks need a metadata refresh to backfill (best-effort field, not retroactively populated)
  - [ ] Show also for episodes
- [x] Make the meetings view anual
  - Tabs, one per year with at least one meeting, defaulting to the current calendar year (falling back to the most recent year with meetings if the current year has none yet). Creating a new meeting switches to its year's tab. Newest year sorts leftmost (per follow-up feedback)
- [x] Give 1 different Columns per person with their ratings
  - [x] Decide a good way to also make clear who rated it what
  - Back to one `TableCell` column per club member (like before the compact-pill-in-title experiment above). Per follow-up feedback: the rating columns now sit right after the title column (position 3, before year/director/runtime/...) instead of at the far end of the table; "who rated what" is a border in that member's own club `color` (see the member-color feature) around the whole cell rather than a header row, which the table deliberately doesn't have
- [x] Show a unfilled shape similar to the current for missing ratings
  - A dashed-outline shape, colored with the member's own color instead of a generic gray. Per follow-up feedback ("a little less compact, at least one square for each"), quality and sentiment each render as their own small square side by side instead of being merged into a single split-color dot
  - Further follow-up: back to one rectangle (34x18) that fills edge-to-edge with the rating color(s) instead of two separate squares with padding/gaps -- solid if only one of quality/sentiment is set, a `linear-gradient` that blends smoothly across the middle 40%-60% band (solid color on either side of that band) if both are. The member-color identification ring moved from a `border` to a thinner (1px) `outline`, since `outline` doesn't eat into the box's own fill area the way `border` would
  - Final redesign: the fixed 40%-60% blend band and the label-less dot are now two user-configurable settings instead
    of fixed choices -- a gradient-blend-percent slider (0-50%) and a fill-with `Number`/`Description` toggle, edited
    via a Tune-icon button next to the page heading, persisted client-side only (`frontend/src/settings/RatingDisplayContext.tsx`,
    `localStorage`) since it's a personal display preference, not club data. Also changes the missing-rating treatment
    above: a half with no rating now shows nothing at all (fully transparent, no text) rather than a dashed placeholder
    or gray track, so the box's fill only ever represents a rating that was actually given. Same design mocked up
    interactively in the artifact used to explore this feature, kept in sync with the shipped settings
  - [ ] Should also have a no fill option, where no text is shown
  - [ ] Always use dashed border for the ratings, even if the user has not added his rating.
- [ ] Make both a light and dark theme available
- [ ] Don't refresh the whole page when rating changes
- [ ] For all colors selections, only allow pastel colors for now
  - [ ] Use this in the UI for the bg and text color should be the stronger version of the color
- [ ] Focus in the next meeting when opening the current year
- [ ] IMDB rating should be episode rating, not series rating
- [ ] If imported series not found on IMDB AND TMDB, do not add them
    - [ ] Twin Peaks is able to load the original 3 seasons, but not the new ones
      - In IMDB they are two different series, in TMDB they are grouped as one
- [x] Use S#E# format for episode prefixes
  - `Episode` only ever carried its own `seasonId`, not the parent season's `number`, so every "Ep. #" spot needed a
    season lookup added: new `GET /seasons/{seasonId}` (`SeasonService.getById`, same club-membership-derived
    permission check as `SeasonService.rate`) plus a frontend `seasonsApi.get`. `SeasonDetailPage` already has one
    fixed `seasonId` from the route, so it just fetches that season directly; `MeetingsPage` and `EpisodeSection`
    (meeting detail) can show episodes from several seasons/series at once, so they use a new shared
    `useSeasonNumbers` hook that resolves every distinct `seasonId` referenced on the page in parallel and returns a
    lookup map. Shared `episodeCode(seasonNumber, episodeNumber)` util (`frontend/src/utils/episode.ts`) renders
    `S#E#`, falling back to just `E#` while the season number is still loading rather than blocking the row on it.
    `EpisodeSearchAutocomplete`'s and the "Up next" suggestion chips' own S#E# labels were already correct (they
    come from `EpisodeSearchResult`, which already denormalizes `seasonNumber`) -- the suggestion chip was switched
    to the same shared util for consistency, `EpisodeSearchAutocomplete` was left as-is
    - [ ] On Display, fill the episode and season numbers with the 0, until the number of the episode is the same as the largest season/episode

# Stretch goals (only start after asked)
- [ ] Use rectangular (flat) country flags instead of the wavy emoji ones
    - Currently `countryFlag()` (`frontend/src/utils/country.ts`) renders Unicode regional-indicator-symbol emoji --
      the wavy/ribbon look isn't something the app controls, it's just how the OS's emoji font draws that character.
      Getting real flat rectangular flags needs an actual flag-icon library (e.g. `flag-icons`, SVG/sprite assets
      addressed by ISO code) swapped in for `CountryFlags` in `MeetingsPage.tsx` + the `countryFlag()` util
- [ ] 
