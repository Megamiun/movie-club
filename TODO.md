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
