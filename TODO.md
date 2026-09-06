# TODO

- [ ] Improve the meeting-picker used when moving a Watchlist movie to a meeting
- [ ] Add member photos (avatars), and try out designs for using them — not a movie/series poster
- [ ] Check how the quality/sentiment rating looks with the full description shown instead of the acronym it
  currently falls back to on phones (`RatingDisplayContext`'s fill-content setting)
- [ ] Add a drag-and-drop icon/handle instead of dragging from anywhere on the row — same ask as the existing
  stretch goal below ("Consider using a drag handle on phone, instead of the whole line")
- [ ] Remove the Movies and Series tabs — their search-and-add capability moves into the meeting "Add" button
  (below) and into the Watchlist's own add flow instead
- [ ] Allow adding a movie straight from someone else's Watchlist onto a meeting (today this only works from your
  own Watchlist)
- [x] Put the watch-link input on its own line below (currently cramped next to another field in the add/edit form)
  - `MovieSection`'s add-movie form had the title/IMDB-id field, the watch-link field, and the Add button all in
    one `Stack direction="row"`, cramped even on a phone-width screen. Now the primary field is its own row, with
    watch-link + Add on the row below it. The edit-details form's own watch-link field was already inside a
    `flexWrap` row, so it wasn't touched.
- [ ] On the meeting page, add an "Add" button that lets you choose movie or series and then follow that specific flow
- [ ] Check the export/share link on desktop
- [x] Improve merge-meeting functionality: show the 4 closest, followed by all others in order.
  - Show dates, not ids
  - `MeetingDetailPage`'s Swap/Merge "Other meeting ID" text box (a raw UUID paste field) is now an `Autocomplete`
    listing every other meeting in the club by its date, ordered via `orderMeetingsByProximity`: the 4 closest by
    date (either direction) first, then everyone else chronologically. Verified in a real browser (Playwright)
    against seeded data: opening the picker for the 2027-12-25 meeting lists 12-18/12-11/12-04/11-27 first, then
    2025-01-05 onward in order.
- [x] Do smarter calendar visualization, try to arrange movies in a more consistent manner(Preferentially all in a line, but if breaking into multiple lines, try to make them better distributed)
  - `CalendarPage`'s poster grid used to be a plain CSS `flex-wrap`, so a month's cards broke unevenly wherever the
    container happened to run out of room (e.g. 7 cards at 5-per-row read as a sparse 5+2). Fixed by measuring the
    grid's container width (new `useContainerWidth` hook) and switching to a CSS grid with a computed column count
    (`balancedColumns`): if all cards fit in one row they stay in one row, otherwise rows are split as evenly as
    possible (7 at a 5-max width becomes 4+3). Card size itself is unchanged (fixed 120px), per this session's own
    call to rebalance rows only, live grid only (not the separate share-image export, which already has its own
    tuned row-gap logic for the same sparse-row problem).
  - Real bug found and fixed while verifying this: `useContainerWidth`'s first version used an object ref plus a
    `useEffect` with `[]` deps to attach the `ResizeObserver`. Since the grid container only renders once
    `meetings` finishes loading (behind an `AsyncState`/`sorted.length === 0` conditional), the effect's one-shot
    check of `ref.current` ran while that element didn't exist yet, so the observer was silently never attached
    and `gridWidth` stayed `0` forever. Fixed by switching to a callback ref, which fires exactly when the element
    actually mounts regardless of when that happens. Confirmed via Playwright: a debug log showed `el = null` on
    the only effect run before the fix, and the grid genuinely never left its 1-column fallback.

- [ ] Consider using a drag handle on phone, instead of the whole line
- [ ] Make the rating box size dynamic (currently a fixed 34x18).

- [ ] Validate how simple we can make minimum metrics, such as response time and status code rates.
    - If simple/cheap, let's do it

- [ ] Member-color and language-preference PATCHes raise the same "one action per click" question the movie/episode
  rating endpoints already answered (a per-field PATCH rather than a full overwrite) — still unresolved.

- [ ] Rating-save code-review leftovers (the optimistic-update work itself shipped — see CLAUDE.md's RatingScale
  section; these are the lower-stakes findings from reviewing it, deliberately not folded in blind):
    - Duplicate `patchMovieReview`/`patchEpisodeReview` (~17 lines each, differ only by collection/id field) —
      a shared generic helper would remove the duplication.
    - The capture-previous/optimistic-patch/rollback dance is hand-inlined separately in `MovieRow.handleSaveRating`
      and `EpisodeRow.handleSaveRating`, and `RatingForm.tsx`'s 4 call sites use a completely different,
      non-optimistic pattern — worth its own pass to extract one reusable optimistic-save hook.
    - `previous` is looked up via a second `pick.reviews.find(...)` scan in `handleSaveRating`, duplicating the
      `review` lookup already computed a few lines below for the same member's cell in the same render pass —
      trivial, bounded by club member count.

# Stretch goals (only start after asked)

- [ ] Drag-and-drop on mobile — meetings table uses `@dnd-kit` (desktop mouse drag works), but touch drag doesn't
  activate (`TouchSensor` never fires under emulated touch). Watchlist's own drag-and-drop hasn't been touch-tested
  either.
- [ ] Rectangular (flat) country flags instead of the emoji ones — needs a real flag-icon library (e.g. `flag-icons`)
  swapped in for `countryFlag()` (`frontend/src/utils/country.ts`), which currently renders Unicode
  regional-indicator emoji.
- [ ] Make import async with a loading state on the meeting list; prioritize movies/series first, then episodes,
  then directors.
- [ ] Spot-with-on-demand-fallback EC2 — run the app instance on Spot with automatic fallback to on-demand when
  capacity isn't available. Needs an EventBridge rule on the Spot interruption warning + a Lambda to launch a
  replacement and repoint the Elastic IP; not attempted, disproportionate to the ~$4-8/month this instance costs
  today (Postgres' own data already survives an interruption either way, via its separate EBS volume).

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
