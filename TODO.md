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
  - [x] Allow removing and adding new ratings to the scale, if removing, need to choose which one will receive the old rating
    - `POST .../rating-scales/{scaleId}/options` to add; `DELETE .../rating-options/{optionId}?reassignToOptionId=...`
      to remove, mandatory reassignment target repointing every existing review across Movie/Series/Season/Episode.
      Can't delete the last option in a scale. `RatingScaleCard` gets an inline add-option row; each option gets a
      delete icon opening a confirm dialog to pick the reassignment target
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
  - [x] Only show suggestions from already running series, or from series in our watchlist
    - New `series.status` column (TMDB's own status string); a series with status "Ended"/"Canceled" is skipped
      unless the club is watchlisting it. Unknown status (not refreshed since this field was added) is treated as
      still running rather than filtered
- [x] Allow Club to have a list of ranked preferred languages
- [x] Allow Club to have a list of ignored languages
- [x] Instead of keeping alternative titles, use translations field, which calls translations API
  - `Movie`/`Series` now store `translations` (TMDB's per-language `/translations` endpoint, via `append_to_response`) instead of `alternative_titles` (per-country); also added `originalLanguage`
- [x] Languages updates should be sent to the BE as soon as changed in the FE
  - Removed the batched "Save language preferences" button; every add/remove/reorder now calls
    `updateLanguagePreferences` immediately, reverting local state on a failed save
- [x] Rotation order should be sent to the BE as soon as changed in the FE
  - Removed the batched "Save order" button; every reorder now calls `updateRotation` immediately, reverting local
    state on a failed save
- [x] Allow Clubs to default to:
  - [x] first available preferred language, or original if unavailable
  - [x] use original title, unless in ignored languages
  - Resolution (`resolveTitle` in `frontend/src/utils/title.ts`) runs client-side, not server-side — it's a pure display concern that only depends on data already in the API response (translations + club's language lists), so it didn't need threading through every read path in `MovieService`/`SeriesService`
  - [x] Is not working everywhere, apply this in every place we use titles
    - Two real gaps found, both server-side -- `ExposedEpisodeRepository.searchByClub` (episode search autocomplete)
      and `EpisodeService.listNextSuggestions` ("Up next" chips) each independently pre-computed a series' label
      server-side as `customTitle ?: originalTitle`, a hand-rolled reimplementation that skipped LANGUAGE-preference
      and preferred/ignored-language resolution entirely -- the two places in the app that show a series name
      without opening its own detail page were silently bypassing every other title-display setting. New
      `EpisodeSearchSeriesTitle`/`EpisodeSearchSeriesTitleResponse` DTOs (replacing the old flat `seriesTitle:
      String`) carry the same title-resolution-capable fields as `SeriesRow` itself (not the full row -- these two
      call sites only ever need a label, not the series' whole catalog data), so `EpisodeSearchAutocomplete` and
      the meeting detail page's "Up next" chips can call the shared client-side `resolveTitle` like everywhere else
      now. Both gained a `languagePrefs` prop threaded down from `MeetingDetailPage`, which didn't reach
      `EpisodeSection` at all before this
    - Every other place that receives a full `Movie`/`Series` object already called `resolveTitle` correctly (audited
      across the whole frontend) -- `Watchlist`/`AdminPage`'s flat title fields are a separate, legitimate
      architectural gap (`WatchlistEntry`/`AdminMediaItem` reference a `MediaItem`, which never carried
      translations/customTitle in the first place -- see Domain Model), not a "forgot to call it" bug, and out of
      scope here
    - Also found and fixed a real, independent bug while investigating this: an uncommitted local edit to
      `resolveTitle` itself had reordered its checks (original-title-if-not-ignored moved before the LANGUAGE/
      preferred-language checks instead of after), which would have broken every one of the call sites above the
      same way regardless of the coverage fix. Restored the correct order: LANGUAGE/preferred-language overrides
      only ever apply once the original title's own language is itself ignored -- if it isn't ignored, and the pick
      isn't CUSTOM, the original always wins outright
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
  - [x] For genres, limit total length, if second genre is too long, also don't show it, should always fit in one line
    - New optional `maxChars` prop -- caps the joined shown-items length, folding anything that'd exceed it into
      the "+N" count instead of wrapping the row. Meetings list genre columns pass `maxChars={20}`. First item is
      always shown in full even alone over budget, so there's never nothing to show
    - [x] Scenarios like 'Animation, Comedy + 1' break a line, should consider the + x when calculating max chars
      - `maxChars` was only ever budgeting the joined shown-items text, not the " +N" suffix appended afterward --
        `TruncatedList` now reserves room for that suffix (`2 + digits(hiddenCount)`) while deciding how many items
        fit, so the total rendered text (items + suffix) never exceeds `maxChars`, not just the items alone.
        Verified: `["Sci-Fi","Adventure","Drama","Horror"]` at `maxChars=17` previously rendered "Sci-Fi, Adventure
        +2" (20 chars, over budget by 3); now renders "Sci-Fi +3" (9 chars, within budget)
- [x] Convert duration to format like '1h25m' or '45m'
  - `frontend/src/utils/duration.ts`, meetings list only for now
  - [x] Align to the right
  - [x] Fill the minutes with spaces(or 0) if the minutes part is just one character
    - Minutes zero-pad to 2 digits, but only alongside an "h" part (`1h05m`) -- a bare `05m` with no hours reads
      oddly and has nothing to align against, so that form stays unpadded
- [x] Only show year on meetings page, but allow to hover to see whole date
  - Clarified: this was about episode air dates, not the meeting's own date. Episode rows show just the year (from `air_date` or the series' `year` as fallback), with the full `air_date` as a hover tooltip when known
- [x] Director should also link to imdb director page
  - [x] Also save them to DB, get from tmdb, save imdb id
  - `director_imdb_id` column on `movies`/`episodes` (V19 migration), resolved best-effort from the credited director's TMDB person id via `/person/{id}/external_ids` (never blocks add/refresh on failure, same as the OMDb rating lookup). `ImdbLink` gained a `kind: 'title' | 'name'` prop for linking to a person's IMDB page instead of a title's. Meetings list only for now, existing picks need a metadata refresh to backfill (best-effort field, not retroactively populated)
  - [x] Show also for episodes
    - Already shipped alongside the movie version, in the same commit (`c4d0648`) -- `MeetingsPage.tsx`'s episode row
      already links `episode.director` through `episode.directorImdbId` the same way the movie row does. This
      checkbox was just stale; found unchecked while scoping the People-table normalization below, no code change
      needed
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
  - [x] Should also have a no fill option, where no text is shown
    - Third `fillWith` option ("No text") -- color fill/gradient unchanged, only the rank/label text is suppressed
  - [x] Always use dashed border for the ratings, even if the user has not added his rating.
    - Outline switched from solid to dashed, and the early-return that hid the whole box (including the outline)
      for a fully unrated, non-editable cell was removed -- the box, and its outline, now always render
- [x] Make both a light and dark theme available
  - Sun/moon toggle in the nav bar using MUI's own `useColorScheme()` (theme already declared `colorSchemes` for
    both). Verified live in a browser -- good contrast throughout, no hardcoded-color issues found
- [x] Don't refresh the whole page when rating changes
  - `useAsync` gained a `silentReload` (refetches without ever setting `loading`, so `AsyncState` never swaps the
    table for a spinner). Meetings table uses it for every pick mutation plus a 5s background poll, so concurrent
    changes from other members show up without a manual refresh or any visible disruption
- [x] For all colors selections, only allow pastel colors for now
  - New shared `PastelColorPicker` (hue-only slider at a fixed pastel saturation/lightness) replaces every native
    `<input type="color">` (member color, rating option color, new-option color). Storage stays a plain hex string.
    Only constrains new picks going forward -- seeded default palettes (deliberately not all-pastel) and any
    pre-existing custom color are left untouched until actually re-picked. Verified live: dragging the picker
    persists a genuinely pastel hex (e.g. `#d1b6ed`) to the DB
  - [ ] Use this in the UI for the bg and text color should be the stronger version of the color
    - Separate from the picker itself -- would mean deriving a second, more saturated color from the stored
      pastel for text/accents wherever it's currently just background. Not attempted here, left for its own pass
- [x] Focus in the next meeting when opening the current year
  - Auto-scrolls the earliest today-or-later meeting into view (centered) once per year-tab selection, not
    re-triggered by the 5s background poll. Verified live: landed exactly on the correct upcoming meeting
- [x] IMDB rating should be episode rating, not series rating
  - `episodes.imdb_rating`, fetched from OMDb via the episode's own `imdb_id` the moment `refreshMetadata` resolves
    it (same best-effort pattern as Movie/Series). Meetings table prefers the episode's own rating, falling back to
    the parent series' rating only when the episode hasn't been refreshed yet
- [x] If imported series not found on IMDB AND TMDB, do not add them
  - Already enforced on both add paths: `SeriesService.addSeries` throws if TMDB has no match for the given IMDB
    id, and `createFromTmdb` (used by both `addSeries` and `addSeriesByTmdbId`) throws if the matched TMDB show has
    no linked IMDB id. No change needed here
  - [x] Twin Peaks is able to load the original 3 seasons, but not the new ones
    - Turned out not to be an IMDB/TMDB grouping mismatch at all -- verified against the live TMDB API: the
      revival ("The Return") is already season 3 of the *same* TMDB show, fully populated. The real bug:
      `importSeasonsAndEpisodes` only ever ran once (wrapped in `runCatching` on initial add) with no way to retry
      it, so a transient TMDB failure partway through left the catalog stuck incomplete forever. Fixed with a
      manual re-import endpoint (see Domain Model, Series → Season → Episode)
  - [ ] Still failing, I get when trying to upload Twin Peaks third season:
    - Row 36: TMDB refresh failed: Could not find TMDB metadata for tt4093826
    - Row 36: Could not import full series from TMDB: Series has not been matched to TMDB yet
    - Row 37: TMDB refresh failed: Series has not been matched to TMDB yet
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
    - [x] On Display, fill the episode and season numbers with the 0, until the number of the episode is the same as the largest season/episode
      - New `GET /seasons/{seasonId}/siblings` (`SeasonService.listSiblingSeasons`) resolves every season sharing
        a season's parent series, straight from the season id (no club-scoped pick id needed, unlike `listSeasons`)
        -- gives the season-number digit width. Episode-number width comes from that specific season's own episode
        list. `useSeasonNumbers` now resolves both per distinct `seasonId`; `episodeCode()` pads each half to its
        width, falling back to unpadded while still loading. Verified live: a 130-episode season renders
        `S0E001`..`S0E130` correctly (season stays unpadded since the series itself only has single-digit seasons)
- [x] Director should be in it`s own normalized table(People probably), and episodes, series and so on, should point to it
  - New `people` table (`name`, `imdb_id` unique-when-set, `tmdb_id` unique-when-set), `PersonRepository`
    (interface/dto/exposed split, same as every other domain) with a single `findOrCreate` -- mirrors
    `MediaItemRepository`'s find-or-create-by-id pattern. `movies`/`episodes` now carry `director_person_id`,
    `series` carries `creator_person_id`, replacing the old inline `director`/`director_imdb_id`/`creator` text
    columns (V26 migration, with a name/imdb_id-deduped backfill of existing data -- early-stage dev data, so the
    no-tmdb-id-yet fallback dedup-by-name is an accepted approximation, same tradeoff V12's MediaItem backfill made)
  - Dedup key is the TMDB person id first (always known from `credits`/`created_by`, before the separate best-effort
    IMDB-id lookup even runs), falling back to the IMDB id alone if no TMDB id was given -- so a person still gets
    one row even before their IMDB id resolves, instead of a fresh row per refresh
  - `MovieRow.director`/`directorImdbId`, `EpisodeRow.director`/`directorImdbId`, `SeriesRow.creator` -- the
    externally-visible field names/shapes -- are unchanged; each `Exposed*Repository` now left-joins `People` to
    reconstruct them at read time instead of storing them inline. Zero API/frontend changes as a result
  - Series creator gets a `creator_person_id` too, but (deliberately, to avoid scope creep) no IMDB-id resolution
    for creators yet -- nothing reads a creator's IMDB id today, so the extra best-effort TMDB-person-external-ids
    round trip wasn't added; TMDB's `created_by` payload already includes each creator's own TMDB person id
    (`TmdbCreator.id`, previously uncaptured) for free, which is enough to dedupe correctly. A later pass can add
    the IMDB lookup the same way director resolution already works
  - Bulk season/episode import (`SeriesService.importSeasonsAndEpisodes`) also resolves each episode's
    `director_person_id` from the same bulk `/tv/{id}/season/{n}` response (which already includes per-episode
    crew) -- but, like the rest of that bulk path, skips the extra per-person IMDB lookup to avoid an API call per
    episode; a full `EpisodeService.refreshMetadata` still resolves the IMDB id later, same as before this change
- [x] Validate/Suggest languages
  - [x] Languages can be just language or have a country, such as pt or pt-BR or pt-PT
  - New `frontend/src/utils/language.ts` (`isValidLanguageCode`/`languageName`/`normalizeLanguageCode`, built on
    `Intl.DisplayNames` the same way `utils/country.ts` already does for country names) accepts both a bare ISO
    639-1 code and a region-qualified one (`pt-BR`/`pt-PT`), rejecting anything `Intl` doesn't recognize with an
    inline error instead of silently no-opping. `LanguageCodeAutocomplete` (shared by both the preferred and
    ignored add-forms in `LanguagePreferencesSection`) suggests from a static ISO 639-1 list, matching on code or
    English name, free-solo so a region-qualified code can still be typed directly. `resolveTitle` updated to
    match a region-qualified preference against a `Translation`'s own `countryCode`, so `pt-BR` actually behaves
    differently from `pt` instead of just being accepted and silently never matching
  - Found and fixed a real, pre-existing bug while testing this live: `LanguagePreferencesSection.persist`'s two
    `setState` calls needed `flushSync` (`react-dom`) -- without it, every single add/remove/reorder was silently
    sending `[]`/`[]` to the backend, wiping both lists (confirmed by intercepting the actual PATCH body; not
    theoretical). `RotationSection.move` has the identical closure-capture pattern for the same documented reason
    and got the same `flushSync` fix as a precaution, even though it wasn't independently observed to fail
- [x] Add icons to the top to show series or episodes, show movies by default, allow for user to turn off or on any
  - Two independent icon toggles (`MovieIcon`/`LiveTvIcon`) next to the Meetings page heading, alongside the
    existing rating-display Tune button. There's no separate "series-only" row to hide independently from
    episodes (a meeting only ever has movie picks and episode picks; the series name is just a grouping label
    above its episodes -- see `PickDragPayload.kind: 'movie' | 'episode'`), so "series" in the UI maps to hiding
    `meeting.episodes` (and, as a consequence, the series grouping headers that have nothing left to group). Both
    default on, matching "movies by default" while not needlessly hiding series either -- the ask was for a way to
    turn types off, not for series to start hidden. Persisted to `localStorage`
    (`frontend/src/pages/MeetingsPage.tsx`'s `MEETING_TYPE_FILTERS_KEY`) as a personal display preference, same
    tier as `RatingDisplayContext`, but plain component state rather than a shared context since nothing outside
    this page needs it. A meeting with real picks that are all currently filtered out still shows its date/
    assigned-member header row (with "Hidden by filters" instead of "Nothing picked yet"), rather than vanishing
    outright -- keeps the list from jumping around as filters are toggled
- [x] If TMDB responds unexpectedly, it should be an error(for example, Unauthorized)
  - `TmdbClient`'s ktor `HttpClient` never set `expectSuccess` (defaults to `false`), so a non-2xx TMDB response was
    decoded as if it succeeded rather than throwing -- and most of this file's response DTOs default their fields
    to empty/null for legitimate "TMDB found nothing" cases (e.g. `TmdbFindResponse.movieResults/tvResults`), so an
    error body (bad API key, rate limit) silently decoded into an *empty* result instead of failing loudly. That's
    almost certainly why a real Unauthorized/rate-limit response was being reported as "Could not find TMDB
    metadata" -- indistinguishable from an actual not-found
  - Fixed with `expectSuccess = true` plus an `HttpResponseValidator` that turns the resulting `ResponseException`
    into a new `UpstreamServiceException` naming the real status (e.g. "TMDB request failed: 401 Unauthorized").
    Mapped to `502 Bad Gateway`, not the upstream's own status code -- deliberately kept distinct from the existing
    `UnauthorizedException` (401), since a TMDB-side 401 means *our* server's API key is misconfigured, not that the
    member calling our API needs to log in; reusing `UnauthorizedException` would've told the frontend the wrong
    thing to do about it
  - `TmdbClient` gained an injectable `engine: HttpClientEngine` constructor param (defaults to real CIO, only ever
    overridden in tests) so this could be verified with `ktor-client-mock` against a real non-2xx response instead
    of only unit-testing the DTOs' parsing logic like the existing tests did
  - `OmdbClient` deliberately left alone -- it already wraps every call in `runCatching { }.getOrNull()` by design
    (IMDB rating is optional and must never block a movie/series add), so silently treating a non-2xx as "no
    rating" is the intended behavior there, not a bug
- [x] Remove notes from media_items
  - No `notes` column ever existed on `media_items` -- verified against every migration and the live DB schema. Only `WatchlistEntry` has a `notes` field (personal commentary, see Domain Model), which is correct as-is. Nothing to remove
- [x] Watch list should have one column per member, always with yours first
  - `WatchlistPage` reshaped into a Trello-style board per section (Movies, Series): one column per club member,
    the viewer's own column always leftmost, others following in the club's usual rotation order. `WatchlistColumn`
    filters+sorts entries by `memberId` client-side; `WatchlistCard` replaces the old flat-list `WatchlistEntryCard`
  - `position` was previously scoped to `(clubId, type)` across *every* member combined (one shared ordered list per
    type) -- rescoped to `(clubId, type, memberId)` so each member's column reorders independently
    (`ExposedWatchlistRepository.create`'s next-position query, `WatchlistService.moveEntry`'s sibling filter). No
    migration needed -- `position` was never DB-unique, so old rows just keep whatever value they already had;
    sorting only ever happens within one member's own filtered subset now
  - Drag-and-drop reordering is still allowed by any club member, not just the entry's owner -- preserved from
    before per-member columns existed (see `WatchlistService.moveEntry`'s doc comment) rather than silently
    tightening it to owner-only, even though it now means reordering someone else's own column
  - Each column gets its own `DndContext` (not one shared context for the whole board), so a card can never be
    dropped into a different member's column in the first place -- entries are personal, ownership isn't
    reassignable via any existing endpoint
  - Removed the `notes` field entirely (backend + frontend) while doing this rework -- V27 migration drops
    `watchlist_entries.notes`, along with `WatchlistService.updateEntry` and `PATCH /watchlist/{entryId}` (notes was
    its only purpose). Per-card free text didn't fit the board layout, and it was the last remaining editable field
    besides position/deletion
- [x] When editing rating, show rating options also colored on the dropdown, displaying text include a colored box
  - New `OptionLabel` (`InlineRatingEditor.tsx`) -- a small circular dot in the option's own `color` next to its
    label, same swatch style already used for rating-option color editing in `ClubOverviewPage`. Used both for
    each dropdown `MenuItem` and, via `Select`'s `renderValue`, the closed select's own display -- so the picked
    color is visible without reopening the dropdown, not just while it's open
- [x] If the screen is too small, default to using only the first letter of the rating inside the meetings table
  - `InlineRatingEditor` now checks `useMediaQuery(theme.breakpoints.down('sm'))` -- only affects the
    `fillWith: 'description'` display mode (the one with a full written label like "Excepcional!", the mode wide
    enough to overflow a small viewport's one-column-per-member layout); `'number'`/`'none'` are already
    single-character/empty and unaffected. On a small screen, `contentFor` shows just `option.label.charAt(0)`
    instead of the full label, and the box itself shrinks back to the compact 34px width (down from 136px) since
    it no longer needs room for a full word
- [x] Write terraform that can set up the infra on AWS
  - `infra/` -- single EC2 instance (t4g.small, arm64) running `db`+`backend` via docker-compose, Caddy on-box
    for automatic HTTPS on `api.<domain>` (Let's Encrypt); frontend build published to a private S3 bucket served
    through CloudFront (`app.<domain>`, ACM cert in us-east-1 via a dedicated provider alias regardless of the
    main `aws_region`). This provisions infra only -- it doesn't build or deploy the app itself, see the GitHub
    Actions item below
  - Backend/frontend split as discussed live: no frontend container at all (it's a static Vite build, no reason
    to run nginx for it) -- only `db`+`backend` run on EC2. `app.<domain>` and `api.<domain>` are separate
    subdomains (not one CloudFront distribution path-routing to both) since a real cert + Caddy on the box gives
    proper end-to-end HTTPS to the backend, not just CloudFront-to-origin-over-HTTP
  - Secrets (JWT secret, DB password, TMDB/OMDb keys) live in SSM Parameter Store (`SecureString`, AWS-managed
    KMS key) rather than baked into EC2 `user_data` -- `user_data` stays visible indefinitely via
    `ec2:DescribeInstanceAttribute` and is cached in plaintext on the instance's own disk, neither of which is
    appropriate for real secrets. The deploy step (GitHub Actions, over SSH) fetches them fresh via
    `fetch-secrets.sh` before every `docker compose up -d`, so nothing sensitive sits at rest in `user_data`
  - Backend image ships through a new ECR repo (10-image lifecycle policy) rather than Docker Hub, so the EC2
    instance only needs a narrowly-scoped IAM role (`AmazonEC2ContainerRegistryReadOnly` + `ssm:GetParameter` on
    exactly its four secrets, nothing broader)
  - Terraform state: S3 bucket with Terraform's native `use_lockfile` locking (needs Terraform >= 1.10) -- no
    DynamoDB table, since that's no longer required for locking on recent Terraform versions. The state bucket
    itself has to be created by hand once before the first `terraform init` (documented in `infra/README.md`),
    since a backend can't provision the bucket it depends on to store its own state
  - Default VPC/subnet, not a dedicated one -- standing up a real VPC (NAT gateway, route tables) is pure added
    cost/complexity for a single EC2 instance
  - Postgres data lives on the EC2 instance's own root EBS volume via docker-compose (not RDS) -- persists across
    stop/start/reboot, but is lost if the instance is ever replaced or the stack is `terraform destroy`'d;
    documented as a known tradeoff in `infra/README.md` rather than adding a second attached volume's complexity
    for a small club's data
  - Follow-up after you asked "is the data secure in EBS?": it wasn't, fully -- the root volume (where Postgres'
    own data lives) had no `encrypted = true`, an oversight, not a deliberate tradeoff. Fixed, using the default
    AWS-managed EBS key (no extra cost/performance cost). Also added IMDSv2 hardening (`metadata_options.http_tokens
    = "required"`) at the same time -- without it, an SSRF-style bug in anything running on the box could fetch the
    instance's IAM credentials from the metadata endpoint with a single unauthenticated GET
  - Validated with `terraform fmt`/`terraform validate` (not applied -- no AWS credentials in this session, and
    provisioning real infrastructure needs your explicit go-ahead regardless)
- [x] Write GH Actions to package and publish to AWS all artifacts, and any actions needed
  - `.github/workflows/deploy-backend.yml` / `deploy-frontend.yml` -- path-filtered on their own half of the repo,
    each with a `test` job (backend: ktlint + `gradlew test`; frontend: `tsc --noEmit` + oxlint) that `deploy`
    `needs:`, so a broken build never reaches production. Both triggers include `pull_request` too (test-only --
    `deploy` is skipped outright on a PR via `if: github.event_name != 'pull_request'`, since the OIDC trust
    policy only allows assuming the role from a push to main anyway) for PR-time feedback, not just push-to-main
  - Backend: builds via `docker/build-push-action` targeting `linux/arm64` under QEMU emulation (`docker/setup-qemu-action`)
    -- caught during this pass: the runner is amd64 but infra's EC2 instance is arm64/Graviton, so a naive build
    would've produced an image that couldn't even run on the box it was deployed to. Pushes to the ECR repo
    Terraform created, then SSHes to EC2 to run the instance's own `fetch-secrets.sh` (refreshes `.env` from SSM
    at deploy time, see Terraform entry above) before `docker compose pull && up -d`
  - Frontend: `npm run build` (bakes `VITE_API_BASE_URL` in at build time, since Vite env vars aren't a runtime
    config), `aws s3 sync --delete` to the frontend bucket, then a CloudFront invalidation so the new build is
    actually visible immediately rather than waiting out the cache TTL
  - Auth is OIDC federation (`infra/github_oidc.tf`), not stored AWS access keys -- the trust policy's `sub`
    condition restricts it to `repo:<owner>/<repo>:ref:refs/heads/main` specifically, so only a push to this
    exact repo's main branch can ever assume the deploy role, not other repos or arbitrary branches/PRs
  - Required repo Variables map directly to `terraform output` values -- documented in `infra/README.md` rather
    than duplicated here, since that's already the natural home for "what to do after `terraform apply`"
  - Follow-up after a code review caught it: the backend deploy step originally used raw SSH, which would never
    have worked -- a GitHub-hosted runner's IP is dynamic per run and could never satisfy the security group's
    fixed `ssh_allowed_cidr` (deliberately scoped to the operator's own IP for interactive admin access only).
    Switched to **SSM Run Command** instead -- authenticates through the same OIDC-assumed role, no inbound port
    needed at all, no SSH key to store as a GitHub secret either
  - Also added `.github/workflows/terraform.yml` (per follow-up ask) so `terraform apply` itself can run via
    CI, not just locally: `plan` runs on every push to main touching `infra/**`, `apply` runs after but is gated
    behind the repo's `production` GitHub Environment (a human has to click approve, having already seen the
    `plan` job's own log). Uses a **separate** IAM role from the app-deploy one (`github_actions_terraform`,
    `infra/github_oidc_terraform.tf`) since running Terraform itself needs much broader permissions (create/modify
    IAM roles, security groups, DNS, ACM certs) -- keeping it a separate role means a compromise of one doesn't
    hand over the other. That role's OIDC trust is also narrower in a different way: only a push to main, *never*
    `pull_request` at all, since this repo is public and a `pull_request`-trusted role is a known OIDC risk there
    (the workflow file for that event is sourced from the PR's own branch, so anyone opening a PR could rewrite it
    to abuse a role trusted at that trigger). A separate `validate` job (`terraform fmt`/`validate`, no AWS
    credentials needed at all) still gives PR-time feedback without extending that trust
  - Validated with `terraform fmt`/`validate` and a plain YAML parse (not run -- no GitHub repo/secrets configured
    to actually trigger these in this session)
- [x] Create a last column that is just a clickable link
  - Meetings table, rightmost column -- an icon-only link to the movie pick's existing optional `watchLink` field
    (HBO/Netflix/magnet link/etc., see `MovieSection`), blank otherwise. Episodes have no equivalent field, so
    `EpisodeRow` always shows the blank fallback rather than a real link -- see Domain Model's Movie section for
    why `watchLink` is movie-pick-only
- [x] Use Episode Link in imdb link, do not inherit from the series
  - Meetings table's `EpisodeRow` was the one remaining place falling back to `series.imdbId` when the episode's
    own `imdbId` wasn't set yet (not refreshed since import) -- `EpisodeSection`/`SeasonDetailPage` already only
    ever used the episode's own id. Now consistent everywhere: no episode imdb id means no link, not a link to the
    wrong (series) page
- [x] Use Episode Rating, duration in meetings page, do not inherit from the series
  - Meetings table's `EpisodeRow` was falling back to `ratingLabel(series)` when the episode had no rating of its
    own yet -- removed, now only ever shows the episode's own rating (blank otherwise), consistent with the IMDb
    link fix above. This reverses what CLAUDE.md previously documented as intentional ("prefers the episode's own
    rating and falls back to the parent series' rating") -- per this explicit ask, that fallback was actually
    misleading (looks like the episode has a rating when it's really the series' aggregate). Duration never
    inherited from series in the first place (Series has no `runtime` field at all -- runtime is per-episode only,
    see Domain Model), so nothing to change there. Year/director/genre/country fallbacks are untouched -- not
    covered by this ask, still documented as intentional
- [ ] Episodes are loaded in wrong order for Cowboy Bebop
  - Example: Even though we have Gateway Shuffle as epi 4, it is imported as Episode 14.
  - Use naming proximity and episode numbers to ensure it is correctly imported

# Stretch goals (only start after asked)
- [ ] Ensure drag and drop work on phones too?
  - Meetings table's drag-and-drop was rewritten from native HTML5 DnD to `@dnd-kit` (`useDraggable`/`useDroppable`
    spanning a `DndContext` at the `MeetingsPage` level, `DragOverlay` for the drag preview, one droppable per row
    within a meeting's block sharing that meeting's id -- same "drop anywhere in this meeting's rows" UX as
    before). Confirmed working with mouse drag on desktop. Confirmed *not* working with touch -- tested via
    Firefox's responsive-design-mode device emulation (Galaxy), where a touch-drag only registers as a page
      scroll, `TouchSensor` never activates. Native HTML5 DnD (the prior implementation) had zero touch support at
    all, so this is still forward progress, just not the full fix -- left as a stretch goal rather than iterating
    further blind (no way to test real touch behavior, or even accurately emulated touch behavior, in this
    session). Watchlist's own drag-and-drop (`@dnd-kit`, a separate simpler non-table implementation) was never
    touch-tested or touched this pass -- still just `PointerSensor`, no `TouchSensor` added
- [ ] Use rectangular (flat) country flags instead of the wavy emoji ones
    - Currently `countryFlag()` (`frontend/src/utils/country.ts`) renders Unicode regional-indicator-symbol emoji --
      the wavy/ribbon look isn't something the app controls, it's just how the OS's emoji font draws that character.
      Getting real flat rectangular flags needs an actual flag-icon library (e.g. `flag-icons`, SVG/sprite assets
      addressed by ISO code) swapped in for `CountryFlags` in `MeetingsPage.tsx` + the `countryFlag()` util
- [ ] Make import async, show loading when looking at meeting list
  - [ ] Prioritize loading movies and series, then episodes and then at last directors
- [ ] Make rating size dynamic
- [ ] Spot-first, on-demand-second EC2 -- run `aws_instance.app` on Spot (cheaper, but AWS can reclaim it with ~2
  minutes' notice), automatically falling back to an on-demand instance whenever Spot capacity isn't available,
  then switching back once it is. Deliberately not the simple `instance_market_options { market_type = "spot" }`
  flag -- that has no built-in on-demand fallback for a single instance (an ASG's `on_demand_base_capacity` either
  means "never Spot" at 1, or "no fallback, just don't launch" at 0). A real fallback needs an EventBridge rule on
  the Spot interruption warning, a Lambda that launches a replacement on-demand instance and repoints the Elastic
  IP, and logic to notice when Spot's available again and switch back -- real infrastructure, disproportionate to
  the ~$4-8/month this instance costs today. Staying on-demand for now (postgres_data's own persistence, see
  Domain/infra notes above, means the *data* survives a Spot interruption fine either way -- this stretch goal is
  purely about minimizing downtime/cost on the compute side, not data safety)
