# TODO

- [x] Merged the Watchlist into one full-width, drag-and-drop-reorderable section per member (viewer's own first,
  movies and series mixed into a single list) — see CLAUDE.md's WatchlistEntry section.
- [x] Added a "← Back to watchlist" link on the meeting detail page, next to the existing "← Back to meetings" one.
- [x] Replaced the Watchlist's plain meeting-id `<Select>` with a proximity-ordered `Autocomplete`, same as the
  swap/merge picker — see CLAUDE.md's WatchlistEntry section.
- [x] Added member photo uploads (S3/MinIO), shown via an opt-in per-viewer toggle — see CLAUDE.md's Member section.
- [x] Checked the acronym fallback for the rating box's full description on phones — verdict: keep it (later
  revisited: split into explicit Description/Initials options, see CLAUDE.md's RatingScale section).
- [x] Moved the meetings-table drag handle onto its own icon column instead of the whole row, matching the
  Watchlist card's own handle — see CLAUDE.md's Movie section.
- [x] Removed the redundant Movies tab (Series tab stays — it's also the only place to browse followed series) —
  see CLAUDE.md's Movie section.
- [x] Allowed moving a movie from any club member's Watchlist onto a meeting, not just your own — see CLAUDE.md's
  WatchlistEntry section.
- [x] Moved the watch-link input onto its own line in the add/edit movie form so it's not cramped on narrow
  screens.
- [x] Added an Add: Movie/Series toggle on the meeting page so the add-movie/assign-episode forms stay hidden
  until chosen — see CLAUDE.md's Movie section.
- [x] Verified the month share/export link on desktop falls back to a plain download correctly — no bug found.
- [x] Replaced the swap/merge "other meeting ID" text box with a proximity-ordered `Autocomplete` showing dates —
  see CLAUDE.md's Schedule Model section.
- [x] Rebalanced the Calendar tab's poster grid rows instead of an uneven `flex-wrap` (and fixed a
  `ResizeObserver` mount-timing bug found while verifying it) — see CLAUDE.md's Schedule Model section.

- [x] Consider using a drag handle on phone, instead of the whole line — done together with the item above.
- [x] Made the rating box size dynamic instead of two fixed guesses (34px/136px) — sized to the scale's widest
  possible content instead, consistent with the acronym-fallback behavior on small screens — see CLAUDE.md's
  RatingScale section.

- [x] Added minimal request metrics (`GET /metrics`, Ktor's Micrometer + Prometheus registry) — see CLAUDE.md's
  Backend Architecture section.

- [x] Split the rating box's "Description" fill mode into explicit Description/Initials options instead of an
  automatic small-screen swap, and cleaned up the rating tooltip — see CLAUDE.md's RatingScale section.

- [ ] Member-color and language-preference PATCHes raise the same "one action per click" question the movie/episode
  rating endpoints already answered (a per-field PATCH rather than a full overwrite) — still unresolved.

- [ ] Rating-save code-review leftovers (the optimistic-update work itself shipped — see CLAUDE.md's RatingScale
  section; these are the lower-stakes findings from reviewing it, deliberately not folded in blind):
  - [x] Extracted a shared `patchPickReview` helper instead of duplicating `patchMovieReview`/`patchEpisodeReview`
    — see CLAUDE.md's RatingScale section.
  - [x] `MovieRow`/`EpisodeRow` now compute `myReview` once instead of each rating handler re-scanning for it.
  - [ ] The capture-previous/optimistic-patch/rollback dance is hand-inlined separately in
    `MovieRow.handleSaveRating`/`EpisodeRow.handleSaveRating`, and `RatingForm.tsx`'s 4 call sites use a
    completely different, non-optimistic pattern — worth its own pass to extract one reusable optimistic-save
    hook. Deliberately not attempted here: `RatingForm.tsx`'s 4 call sites (Series/Season/Episode/MovieSection's
    combined `rate` endpoint) have a genuinely different shape than the split quality/sentiment PATCH pattern
    used here, and generalizing across both risks introducing a regression in code this pass didn't otherwise
    touch. Left as its own follow-up.

- [x] Added on-demand (`POST /admin/metadata-refresh`) and nightly TMDB/OMDb metadata refresh, budgeted against
  OMDb's daily quota — see CLAUDE.md's MediaItem section.

# Stretch goals (only start after asked)

- [ ] Drag-and-drop on mobile — meetings table uses `@dnd-kit` (desktop mouse drag works), but touch drag doesn't
  activate (`TouchSensor` never fires under emulated touch). Watchlist's own drag-and-drop hasn't been touch-tested
  either.
- [ ] Rectangular (flat) country flags instead of the emoji ones — needs a real flag-icon library (e.g. `flag-icons`)
  swapped in for `countryFlag()` (`frontend/src/utils/country.ts`), which currently renders Unicode
  regional-indicator emoji.
- [ ] Make import async with a loading state on the meeting list; prioritize movies/series first, then episodes,
  then directors.
- [ ] Spot EC2 instead of on-demand — revisit if/when the current free-tier credit covering `BoxUsage:t4g.small`
  runs out. Checked via Cost Explorer (`aws ce get-cost-and-usage`, filtered to `EC2: Running Hours`): the
  instance-hours themselves are genuinely billing $0 right now (finalized, not a reporting lag), so switching to
  Spot today wouldn't save anything — held off for that reason, not because it's hard.
  - Design simpler than originally assumed, worth remembering next time: a *persistent* Spot request with
    `instance_interruption_behavior = "stop"` (not `terminate`) needs no "launch a replacement and repoint the
    Elastic IP" machinery at all — AWS stops and later restarts the *same* instance (same instance id, same EBS
    volumes, same EIP association), and `aws_eip.app`'s own comment already documents that the IP survives a
    stop/start cycle, since that's the exact same mechanism. Just add an `instance_market_options { market_type =
    "spot"; spot_options { instance_interruption_behavior = "stop"; spot_instance_type = "persistent" } }` block
    to `aws_instance.app`.
  - Real trade-off, not eliminated by the above: no automatic on-demand fallback while Spot capacity is
    unavailable — the app is just down until AWS resumes the instance (usually fast for t4g.small, not
    guaranteed). Converting the existing on-demand instance to Spot also isn't in-place — purchase option is set
    at launch, so Terraform has to destroy and recreate the instance, with real downtime during the switch itself
    (a silver lining: that reruns `user_data` fresh, so backups/CloudWatch logs would auto-configure instead of
    needing another manual backfill like this session's).
- [ ] Analyse going IPv6-only on the EC2 instance to drop AWS's flat public-IPv4 charge (~$0.12/day, confirmed via
  `samples/costs.csv`'s daily "VPC" line item — $0.005/hr since AWS's Feb 2024 pricing change, applies regardless
  of whether the IP is an Elastic IP or just auto-assigned). Real trade-off already identified, not yet resolved:
  the API would only be reachable over IPv6, and some club members' networks (older ISPs, some mobile carriers,
  corporate networks) may be IPv4-only and would simply be unable to reach it at all — not slower, just broken.
  Also needs confirming TMDB/OMDb's APIs are themselves IPv6-reachable for the backend's own outbound calls, and
  moving `ssh_allowed_cidr` to an IPv6 CIDR. Initial read: for a small friend-group app, ~$43/year isn't worth a
  real chance of breaking access for someone — but worth actually checking members' connectivity before deciding.

- [ ] Make color selector more inclusive, should allow for a certain range of colors. Grill me
    - Grilling so far: this is about widening `PastelColorPicker`'s fixed S60/L82 point, not accessibility/
      colorblindness. Confirmed goal: land close to the *existing* seeded palette colors (`DEFAULT_QUALITY_COLORS`/
      `DEFAULT_SENTIMENT_COLORS`, `ClubService.kt`), not full RGB freedom, and exclude both extremes (too
      heavy/saturated-dark, too light/washed-out).
    - Measured the actual HSL spread of both palettes to ground this (see published artifact from that session,
      `rating-scale-colors.html`): **Quality** (bold/dark) spans `L 23–51%`, nowhere near the picker's fixed 82% —
      only a lightness-widening reaches it. **Sentiment** (pastel) alone spans `S 31–100%` at an almost-unchanged
      `L 81–89%` — only a saturation-widening reaches it. Neither single axis covers both scales; only opening both
      (a clamped 2D saturation×lightness square, not just a second slider) gets close to the full existing palette.
    - Open question, not yet answered: is Quality's darker/bolder range actually meant to be reachable through this
      same "inclusive" picker, or does this only need to cover Sentiment-style (pastel-family) colors, with Quality
      staying out of scope? That decides whether a single saturation-only slider is enough or the full 2D square is
      needed. Pick back up here next time.
