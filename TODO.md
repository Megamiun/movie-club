# TODO

- [x] Added a "← Back to watchlist" link on the meeting detail page, next to the existing "← Back to meetings" one.
- [x] Moved the watch-link input onto its own line in the add/edit movie form so it's not cramped on narrow
  screens.

- [ ] Member-color and language-preference PATCHes raise the same "one action per click" question the movie/episode
  rating endpoints already answered (a per-field PATCH rather than a full overwrite) — still unresolved.

- [ ] Rating-save code-review leftovers (the optimistic-update work itself shipped — see CLAUDE.md's RatingScale
  section; these are the lower-stakes findings from reviewing it, deliberately not folded in blind):
  - [ ] The capture-previous/optimistic-patch/rollback dance is hand-inlined separately in
    `MovieRow.handleSaveRating`/`EpisodeRow.handleSaveRating`, and `RatingForm.tsx`'s 4 call sites use a
    completely different, non-optimistic pattern — worth its own pass to extract one reusable optimistic-save
    hook. Deliberately not attempted here: `RatingForm.tsx`'s 4 call sites (Series/Season/Episode/MovieSection's
    combined `rate` endpoint) have a genuinely different shape than the split quality/sentiment PATCH pattern
    used here, and generalizing across both risks introducing a regression in code this pass didn't otherwise
    touch. Left as its own follow-up.

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
