# TODO

- [x] Update instantly when changing languages, colors, rating and so on, but just the relevant components
  - Language-preference edits now refresh the shared `club` object (`LanguagePreferencesSection` calls the outlet's
    `silentReload`, same pattern as member color); `ClubLayout` polls that `club` fetch every 15s; `MeetingsPage`/
    `MeetingDetailPage`/`SeriesDetailPage`/`SeasonDetailPage` each fold their rating-scales fetch's `silentReload`
    into their existing poll. See CLAUDE.md's RatingScale section.
- [ ] Make color selector more inclusive, should allow for a certain range of colors. Grill me
  - Grilling so far: this is about widening `PastelColorPicker`'s fixed S60/L82 point, not accessibility/
    colorblindness. Confirmed goal: land close to the *existing* seeded palette colors (`DEFAULT_QUALITY_COLORS`/
    `DEFAULT_SENTIMENT_COLORS`, `ClubService.kt`), not full RGB freedom, and exclude both extremes (too
    heavy/saturated-dark, too light/washed-out).
  - Measured the actual HSL spread of both palettes to ground this (see published artifact from this session,
    `rating-scale-colors.html`): **Quality** (bold/dark) spans `L 23–51%`, nowhere near the picker's fixed 82% —
    only a lightness-widening reaches it. **Sentiment** (pastel) alone spans `S 31–100%` at an almost-unchanged
    `L 81–89%` — only a saturation-widening reaches it. Neither single axis covers both scales; only opening both
    (a clamped 2D saturation×lightness square, not just a second slider) gets close to the full existing palette.
  - Open question, not yet answered: is Quality's darker/bolder range actually meant to be reachable through this
    same "inclusive" picker, or does this only need to cover Sentiment-style (pastel-family) colors, with Quality
    staying out of scope? That decides whether a single saturation-only slider is enough or the full 2D square is
    needed. Pick back up here next time.
- [ ] Let a MediaItem be linked back to its real underlying Movie/Series catalog row, and used as that type — right
  now MediaItem only carries a flat `title` (no `translations`/`originalLanguage`), so anything holding just a
  MediaItem (Watchlist today) can't resolve a proper display title. `resolveTitle` isn't callable on Watchlist
  entries as a result — see CLAUDE.md's Movie section. Movie/Series already point *to* MediaItem
  (`media_item_id`); this is the reverse lookup (e.g. `MovieRepository`/`SeriesRepository.findByMediaItemId`,
  composed in `WatchlistService`, same as any other cross-entity orchestration).
  - Preference for this and future cross-type work: wherever an operation is common across Movie/Series/Episode
    (not just this reverse lookup), prefer exposing it once on the shared MediaItem endpoints rather than
    duplicating it per type.
- [ ] Validate how simple we can make minimum metrics, such as response time and status code rates. 
  - If simple/cheap, let's do it
- [x] Rating a movie/episode felt slow to update. Root cause: `InlineRatingEditor`'s save had zero optimistic
  update — `MeetingsPage.handleSaveRating` `await`ed the rating PUT, then only *after* that called
  `onChange`/`silentReload`, which refetched the **entire** club's meeting history just to show one cell's new
  value. Two full round-trips gated visible feedback for a one-cell change.
  - Fixed: `useAsync` gained a `setData` functional setter; `MeetingsPage` uses it to patch the one review that
    changed directly in local state *before* the request fires (`patchMovieReview`/`patchEpisodeReview` +
    `upsertReview`), rolling back to the captured previous value only on failure — the same optimistic pattern
    `RotationSection`/`LanguagePreferencesSection` already used elsewhere on this page. No reload call at all on
    the success path now (the poll still reconciles regardless); the row-level `onChange` prop this replaced was
    otherwise unused in `MovieRow`/`EpisodeRow` (drag-and-drop lives at the page level), so it was removed rather
    than left dead.
  - Code review of that commit found two real bugs, both fixed: (1) rollback race — a failed save's rollback
    unconditionally restored a captured snapshot, which could clobber a second, already-succeeded concurrent save
    on the same cell (e.g. quality-then-sentiment clicked quickly); fixed with a `matchesCurrent` compare-and-swap
    guard so a rollback is a no-op once a newer save has already moved the review away from what it originally
    wrote. (2) `comment` silently wiped on every rating-only save — a pre-existing bug (`RateMovieRequest.comment`
    defaults to `null`, `ExposedMovieRepository.upsertReview` overwrites all three columns unconditionally), made
    worse by this diff (the optimistic patch preserved the old comment locally, hiding the wipe for up to the next
    10s poll instead of surfacing it within one round trip); fixed by echoing `previous?.comment` through on every
    `moviesApi.rate`/`episodesApi.rate` call so it's never actually cleared.
  - Also fixed from that review: `patchMovieReview`/`patchEpisodeReview` used to `.map()` (allocating a callback
    result for) every meeting and every pick in the club's whole history on every single rating click, just to
    replace the one that changed. Both now `findIndex` their target meeting and pick directly and replace just
    that one slot in a copy of the two arrays involved -- the rest of the club's history is never touched.
  - Also fixed: the referential-equality preservation the `findIndex` rewrite above gives unaffected rows wasn't
    actually paying off anywhere, since `MeetingRows`/`MovieRow`/`EpisodeRow` weren't memoized -- every row still
    re-rendered on every rating click regardless. All three now wrapped in `React.memo`; `patchMovieReview`/
    `patchEpisodeReview` wrapped in `useCallback` and the previously-inline-per-meeting `registerRow` closure
    lifted to one stable `useCallback` (`registerRow(meetingId, el)`, called by `MeetingRows` as
    `(el) => registerRow(meeting.id, el)`) so their references stay stable across `MeetingsPage` renders too --
    without that, `onRate`/`registerRow` changing identity on every render would have defeated the memoization
    entirely. `scales` still gets a fresh array reference on every 10s poll regardless of content, so rows still
    re-render on that cadence either way -- not something this pass changes.
  - [ ] Remaining lower-stakes findings from that same review, not yet acted on:
    - Duplicate `patchMovieReview`/`patchEpisodeReview` (~17 lines each, differ only by collection/id field) --
      a shared generic helper would remove the duplication.
    - The capture-previous/optimistic-patch/rollback dance is hand-inlined separately in `MovieRow.handleSaveRating`
      and `EpisodeRow.handleSaveRating`, and `RatingForm.tsx`'s 4 call sites use a completely different,
      non-optimistic pattern -- worth its own pass to extract one reusable optimistic-save hook, not folded in blind.
    - `previous` is looked up via a second `pick.reviews.find(...)` scan in `handleSaveRating`, duplicating the
      `review` lookup already computed a few lines below for the same member's cell in the same render pass --
      trivial, bounded by club member count.
- [x] Fix the N+1 in `GET /clubs/{clubId}/meetings` (also flagged by that review, separate from the frontend
  findings above -- this one's backend). `MeetingService.listMeetings` → `withPicks()` used to run, per meeting:
  `movieRepository.listByMeeting` then per movie `listReviews`; `episodeRepository.listByMeeting` then per episode
  `findSeriesImdbId` + `seriesRepository.findByClubAndImdbId` + `listReviews` -- roughly
  `O(meetings + movies + episodes×3)` queries for one request, hit on every page load *and* every 10s poll tick.
  - Fixed: added batched `listByMeetings`/`listReviewsByMovies` (`MovieRepository`), `listByMeetings`/
    `listReviewsByEpisodes`/`findSeriesImdbIds` (`EpisodeRepository`), `findByClubAndImdbIds` (`SeriesRepository`)
    -- each an `inList` query (already the codebase's own convention for batch lookups, e.g. the integration
    tests' cleanup helpers), short-circuiting on an empty input list before touching Exposed at all. `withPicks()`
    replaced with one `loadPicks(meetings: List<MeetingRow>)` that both `listMeetings` (the whole club) and
    `getMeeting` (a list of one) now share -- one code path instead of two, and `getMeeting` gets the same fix
    for free. Query count drops from scaling with club history to a fixed ~6 regardless of size.
  - New repository integration tests (real Postgres via Testcontainers) per batch method, including the
    empty-input-list case; a new `MeetingServiceTest` case specifically covers the regression this kind of
    rewrite risks -- a movie belonging to one meeting showing up grouped under a different one once the fetch is
    batched across meetings instead of done per-meeting. `./gradlew :backend:test`/`:backend:ktlintCheck` both
    pass. Not manually verified against the running app (docker compose) -- automated coverage only.
- [ ] Separately (not yet done): `PUT /movies/{id}/review` (and the series/season/episode equivalents) is a full
  overwrite of both quality *and* sentiment together, not independent per-field — `InlineRatingEditor` already has
  to read the untouched field back out of its own props to avoid clobbering it on every save. Doesn't affect
  perceived speed now that saves are optimistic, but still an open question: worth making genuinely independent
  (separate endpoint/param), or is the frontend papering over it fine long-term? Same question extends to the
  member-color and language-preference PATCHes ("Make APIs do one action per click" — Ratings / Colors / Languages
  / PATCH? DELETE? PUT? — still unresolved).

# Stretch goals (only start after asked)

- [ ] Drag-and-drop on mobile — meetings table uses `@dnd-kit` (desktop mouse drag works), but touch drag doesn't
  activate (`TouchSensor` never fires under emulated touch). Watchlist's own drag-and-drop hasn't been touch-tested
  either.
- [ ] Rectangular (flat) country flags instead of the emoji ones — needs a real flag-icon library (e.g. `flag-icons`)
  swapped in for `countryFlag()` (`frontend/src/utils/country.ts`), which currently renders Unicode
  regional-indicator emoji.
- [ ] Make import async with a loading state on the meeting list; prioritize movies/series first, then episodes,
  then directors.
- [ ] Make the rating box size dynamic (currently a fixed 34x18).
- [ ] Spot-with-on-demand-fallback EC2 — run the app instance on Spot with automatic fallback to on-demand when
  capacity isn't available. Needs an EventBridge rule on the Spot interruption warning + a Lambda to launch a
  replacement and repoint the Elastic IP; not attempted, disproportionate to the ~$4-8/month this instance costs
  today (Postgres' own data already survives an interruption either way, via its separate EBS volume).
- [ ] Consider using a drag handle on phone, instead of the whole line
