# TODO

- [x] Added a "← Back to watchlist" link on the meeting detail page, next to the existing "← Back to meetings" one.
- [x] Moved the watch-link input onto its own line in the add/edit movie form so it's not cramped on narrow
  screens.

- [ ] When resolving a movie title, if the original is excluded, but there is no preferred title in a preferred language, still use original

- [x] "Animation, Comedy +1" and "Drama, Adventure +1" still break in two lines — `TruncatedList`'s char-count
  budget is only an approximation of rendered width, so a string sitting right at the boundary could still be a
  hair too wide; added `whiteSpace: nowrap` to the Genre cell as a hard guarantee on top of it.

- [ ] Feedback from Camila Defensor (2026-09-13), grouped by area:
    - Watchlist:
        - [ ] Fix the meeting-selector icon on the Watchlist page — the arrow renders crooked/misaligned and it's
          visually bothersome.
        - [ ] Clicking the Watchlist's meeting selector doesn't change the cursor to a pointer — it should look
          clickable/actionable on hover.
        - [x] Change the IMDB link icon on the Watchlist to the actual IMDB logo/icon instead of the current generic
          one. Fixed via a shared `ImdbIcon` component, so this also covers the same ask on the Meetings table below.
        - [ ] Add a movie/series add box at the top of the Watchlist page, and allow any member to add entries to
          anyone's list (not just their own section).
    - Date formatting:
        - [x] Change the default date picker format to "13 SEP 2026" style (day, abbreviated month, year). Shipped as
          "13 Sep 2026" (title case, matching the app's English UI) via a new `DateDisplayContext` + `formatMeetingDate`
          util, applied to the Meetings table and the Watchlist's "move to meeting" picker.
            - [x] Add a setting for it, can be 13 Sep 2026 or 2026-09-13 — a nav-bar icon toggle next to the theme/photo
              toggles, `localStorage`-persisted like those.
    - Meetings (table page):
        - [x] Add a visual indicator for the current week's meeting — a small "This week" calendar-icon badge next to
          the date (`isCurrentWeek`, Monday-Sunday).
        - [x] Change the date format shown in the Meetings table — same `DateDisplayContext` fix as above.
        - [x] Change the IMDB link icon to the actual IMDB logo/icon (same ask as Watchlist above) — same shared
          `ImdbIcon` fix.
        - [x] Remove the dashed border around each member's rating block — dropped entirely once at least one rating
          is set (the fill color alone defines the box then); kept dashed only for a fully-unrated box, since it
          would otherwise be invisible with nothing to click.
        - [x] Fix rating text overflowing its box (e.g. "Excepcional" exceeds the box's edges) in Description fill mode.
            - [x] Suggestion: Also allow for it to be inside gradient, not only on the solid color — implemented: the
              color fill is now a single background gradient, with the two text labels laid out as a fixed 50/50 split
              on top (always the full half-width, gradient band included), instead of shrinking with the blend percent.
        - [x] Adjust the message/icon shown for a week with no movie — stop showing the "Nothing picked yet" text.
          Removed outright rather than reworded (CLAUDE.md's Schedule Model already documents an empty future slot as
          expected, not a gap worth flagging).
        - [x] Improve/clean up the link from a Meetings row to the meeting detail page — the date link only existed on
          an empty meeting's row; now every meeting's date links to its detail page consistently, pick or no pick.
    - Meeting detail page:
        - [ ] Remove the "assigned member" display at the meeting level.
        - [ ] Remove the empty-session info block.
        - [ ] Increase the poster size in the movie list on this page.
        - [ ] Clicking a movie in the list should expand that movie's own block and enlarge the same poster further,
          rather than adding a separate/new poster.
            - On mobile portrait mode, keep a row just for the poster
        - [ ] Add line breaks between each piece of textual info shown next to the banner/poster.
            - [ ] First line is user photo, title, country flags
        - [ ] Move the action buttons to sit above the poster.
        - [ ] Turn the custom-title feature into an icon; editing the title happens in a modal instead of inline.
        - [ ] Turn the watch-link feature into an icon; editing the link happens in a modal instead of inline.
        - [ ] Below the poster, add the viewer's own photo plus a rating icon next to it — the icon should stay visible
          even after a rating has been given, so it can still be used to edit the existing rating.
        - [ ] Remove the "other meeting" button.
        - [ ] Turn the add movie/series action into an icon that opens a modal.
        - [ ] Turn the delete action into an icon that opens a modal.
        - [ ] Turn the swap fields into an icon, with the swap performed inside a modal.
        - [ ] In the movie block's background, try a gradient built from the poster's own colors.

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
