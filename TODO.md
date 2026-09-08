# TODO

- [x] Keep watchlists in a way that you see you first, occupying the whole screen horizontally, until your watchlist ends, then put the other users, in rotation order from your user one
  - [x] Also, mix series and movies
  - [x] Order change will be drag and drop now
  - Clarified with the user first (3 real forks here): the layout is a vertical stack of full-width sections
    (each row fits as many cards as the available width allows, not a fixed column count); the up/down buttons
    are gone entirely, drag-and-drop only; and the one-time merge of the two previously-separate, independently-
    ordered lists (movies had their own position sequence per member, series had their own) puts movies first,
    series after.
  - Backend: `watchlist_entries.position` was scoped to `(club, member, MediaItem type)` — two independent
    orderings per member. Dropped `type` from that scope in both `ExposedWatchlistRepository.create` (a new
    entry now appends to the end of the member's one mixed list, either type) and
    `WatchlistService.moveEntry` (adjacent-swap siblings are now just "same member," not "same member and same
    type"), so a movie and a series can now sit next to each other and swap directly. New migration
    (`V29__watchlist_mixed_position.sql`) renumbers every existing row per `(club, member)` in one pass —
    movies keeping their relative order first, series keeping theirs after, exactly the merge rule above.
  - Frontend: `WatchlistPage` no longer renders two side-by-side `WatchlistBoard`s (Movies, Series) each with
    their own row of fixed-260px member columns. Now one `WatchlistMemberSection` per member (viewer's own
    first, then rotation order), each a full-width `display: grid; grid-template-columns: repeat(auto-fill,
    minmax(150px, 1fr))` — CSS handles the "fit as many as the row allows" sizing on its own, no JS measurement
    needed (unlike the Calendar tab's `balancedColumns`, which solves a different problem — avoiding a sparse
    *last* row in a static, non-interactive grid; drag-and-drop reordering doesn't benefit from that kind of
    rebalancing). Reused `@dnd-kit/sortable`'s `rectSortingStrategy` instead of `verticalListSortingStrategy`,
    since cards now wrap into a grid instead of stacking in one column — the actual reorder mechanism
    (adjacent-swap, replayed once per step for a multi-slot drag) is unchanged. Each member still gets their own
    `DndContext`, so a card still can never be dropped into a different member's section. The add form (search +
    add) used to be two separate instances, one per type-specific board; now it's one instance inside the
    viewer's own section with a Movie/Series toggle (same `ToggleButtonGroup` pattern used for the meeting
    page's own Add button earlier this session) choosing which `TmdbSearchAutocomplete` to show.
  - Verified against the real running app: added a movie then a series and confirmed positions landed
    sequentially (0, 1, 2) across both types rather than each restarting its own count; moved a movie up to swap
    with an adjacent series entry via the API directly and confirmed the swap crossed the type boundary; in the
    browser, confirmed section order (own name first), grid wrapping at both a 1100px desktop width (6 cards
    per row) and a 390px mobile width (2 per row, no more side-scrolling), and that delete/move-to-meeting still
    work through the new card layout.
- [x] Add a back to wishlist button on meeting page
  - `MeetingDetailPage` already had a "← Back to meetings" link at the top; added a matching "← Back to
    watchlist" one right beside it (`/clubs/{clubId}/watchlist`), same style. Verified in a real browser: click
    navigates there correctly.
- [x] Improve the meeting-picker used when moving a Watchlist movie to a meeting
  - Same complaint as the swap/merge picker above (a long flat list of every meeting, nothing prioritized) —
    `WatchlistCard`'s "Move to meeting" control was a plain `<Select>` listing every club meeting in ascending
    date order. Extracted the swap/merge fix's ordering logic into a shared `orderMeetingsByProximity`
    (`frontend/src/utils/meetings.ts`) and reused it here anchored to *today* (there's no "current meeting" to
    measure against on the Watchlist), then swapped the plain `Select` for an `Autocomplete` like the swap/merge
    one. Verified in a real browser: with today = 2026-09-06 and a mostly-2025/2026/2027 seeded schedule, the
    picker orders 2026-09-05, 2026-09-12, 2026-08-29, 2026-09-19 first, then 2025-01-05 onward.
- [x] Add member photos (avatars), and try out designs for using them — not a movie/series poster
  - Clarified with the user first: photos come from a real upload (not a pasted URL), and where they show up is
    controlled by a personal toggle rather than baked into `MemberBadge` unconditionally.
  - `Member` gained `photoS3Key` (global, not per-club, unlike `color` — a photo is identity, not club-specific
    styling) plus `S3StorageClient` (`service/storage/`), the first real use of the S3 upload this app's schema
    has had unused columns for since `poster_s3_key` (see CLAUDE.md's MediaItem section — posters still never
    touch S3, served straight from TMDB's CDN). Upload is self-service only
    (`MemberService.uploadPhoto` — `memberId == actingMemberId` or 403), validates content-type
    (jpeg/png/webp) and a 5MB size cap, and stores under `member-photos/{memberId}/{random}` via a new
    `POST /members/{memberId}/photo` (multipart, one file field). `S3StorageClient` lazily creates the bucket
    and sets a public-read bucket policy on first use, since a photo needs to load directly in an `<img src>`.
  - Local dev needed an actual S3-compatible store to test against (no real AWS creds configured, and posters
    never having used S3 meant there was nothing to reuse) — added a `minio` service to `docker-compose.yml`
    (`docker compose up -d minio`), with `S3_ENDPOINT_URL`/`S3_PUBLIC_BASE_URL` env vars (new, optional — unset
    for a real deployment, which talks to real AWS S3 with its default endpoint/virtual-hosted URLs instead).
  - Frontend: `photoUrl` threaded through `Member`/`ClubMember`/`MemberSummary` and their backend responses. A
    new `MemberPhotoContext` (`localStorage`-persisted, same pattern as `RatingDisplayContext`) holds a
    `showPhotos` toggle, defaulting *off* — initials are uniform everywhere already, while photos are opt-in per
    member, so defaulting "on" would look inconsistent (some cells photo, some initials) until every member has
    uploaded one. `MemberBadge` passes `photoUrl` to MUI's `Avatar` only when `showPhotos` is on; a broken/missing
    URL falls back to the existing colored-initials rendering automatically (`Avatar`'s own `<img>`-error
    fallback). Upload itself happens by clicking the viewer's own avatar in the nav bar (`AppLayout`'s new
    `OwnPhotoUploader`) — there's no separate profile page yet, and a photo is the only account fact that's ever
    user-editable here. A new `PersonIcon`/`PersonOutlineOutlined` toggle button next to the theme toggle switches
    `showPhotos` globally.
  - Verified end-to-end against the real running app (not mocked): started MinIO, uploaded a real PNG via `curl`
    first to confirm the backend chain (bucket auto-create, public-read policy, key construction) before touching
    the UI, then downloaded the resulting URL anonymously (no auth header) and got the exact same bytes back,
    confirming the bucket policy actually works. In the browser: uploaded a photo via the nav avatar, confirmed
    it rendered as a real `<img>` immediately (no reload), enabled the "show photos" toggle, and confirmed the
    meetings table's `MemberBadge` cells switched from colored initials to real `<img>` elements (29 of them) —
    while a member with no uploaded photo correctly kept showing their colored initials.
- [x] Check how the quality/sentiment rating looks with the full description shown instead of the acronym it
  currently falls back to on phones (`RatingDisplayContext`'s fill-content setting)
  - Checked by temporarily disabling `InlineRatingEditor`'s small-screen truncation and screenshotting a real
    390px-wide session with real rated 2025 data, then reverting. Verdict: keep the existing acronym fallback —
    showing the full label forces each rating box to its full 136px width (`isCompact` false), and on a 390px
    phone that's wide enough that only the *first* member's rating column fits on screen at all; every other
    member's column scrolls off entirely, which defeats the point of a table meant for comparing everyone's
    ratings at a glance. The single-letter fallback was already the right call; no code change made.
- [x] Add a drag-and-drop icon/handle instead of dragging from anywhere on the row — same ask as the existing
  item below ("Consider using a drag handle on phone, instead of the whole line"); done together.
  - Watchlist cards already had a dedicated `DragIndicatorIcon` handle (`{...attributes} {...listeners}` on just
    that icon, not the whole `Paper`), so this was really just the Meetings table: `MovieRow`/`EpisodeRow` had
    `{...attributes} {...listeners}` spread on the whole `TableRow`, so the entire row (including e.g. empty space
    in cells with nothing in them) was a drag source, `cursor: grab` and all. Moved the drag `attributes`/
    `listeners` onto a new leading icon-only column (`DragIndicatorIcon`, matching Watchlist's own pattern) instead
    — `useDraggable`'s `setNodeRef` stays on the row itself (dnd-kit's own documented handle pattern: the node
    marks the draggable region for hit-testing, only the pointer-listeners need to live on the smaller handle).
    `columnCount` bumped from `10 + members` to `11 + members`; the two places that render a full-width message
    row ahead of the Date column (`MeetingDropRow`, the first-episode-group series-label row) got a matching
    leading blank cell and their `colSpan` dropped by one more to match. Verified in a real browser: the handle
    renders as its own narrow column on every row, and clicking a movie/episode title still navigates to the
    meeting detail page (the click handler's `stopPropagation` was never dependent on where the drag listeners
    lived, so this kept working unchanged).
- [x] Remove the Movies and Series tabs — their search-and-add capability moves into the meeting "Add" button
  (below) and into the Watchlist's own add flow instead
  - Scoped down after a real snag found while planning this: the Series tab isn't just a search-and-add form like
    Movies — it's also the *only* place in the app that lists a club's already-followed series and links into a
    series' detail page (seasons/episodes, language settings, metadata refresh). Nothing else links to
    `/series/:id`. Removing it outright would strand that browsing capability with nowhere to go, so per this
    session's own decision: only `MoviesPage` (genuinely just a redundant search-and-add form — picks are already
    visible via Meetings/Calendar, and its capability is now fully covered by the meeting page's own "Add" button
    above and the Watchlist's existing add form) was removed. The Series tab stays.
  - Deleted `MoviesPage.tsx`, its `movies` tab entry (`ClubLayout`) and route (`App.tsx`) — confirmed nothing else
    referenced it. Visiting the old `/clubs/{id}/movies` URL now falls through to the app's existing catch-all
    route (redirects to `/clubs`), not a new dead end. Verified in a real browser: club nav shows Meetings/Series/
    Watchlist/Calendar/Import/Overview (6 tabs, Movies gone), the old URL redirects cleanly.
- [x] Allow adding a movie straight from someone else's Watchlist onto a meeting (today this only works from your
  own Watchlist)
  - `WatchlistCard`'s "Move to meeting" picker was gated behind `isOwner`, matching the deliberate rule documented
    in CLAUDE.md ("owner-only in the watchlist-to-meeting direction"). That rule now only applies to outright
    deleting an entry — moving one to a meeting is open to any club member, the same way any member can already
    add a brand-new movie to a meeting from scratch.
  - This needed a real backend change, not just dropping the frontend's `isOwner` check: the existing move
    composed two separate calls (`moviesApi.add` then `watchlistApi.remove`), and `remove`'s backend
    (`WatchlistService.deleteEntry` → `requireOwnedEntry`) is still, and should stay, owner-only for a raw delete.
    A non-owner's move would have added the movie fine, then hit a 403 on the delete half, leaving the movie
    duplicated in both the meeting and the original owner's watchlist. Added a new atomic
    `WatchlistService.moveEntryToMeeting` (backed by a new `POST /watchlist/{entryId}/move-to-meeting/{meetingId}`,
    reusing `MovieService.addMovie` — a Service depending on another Service, same established pattern as
    `MovieService` already depending on `ClubService`) that checks only club membership, not ownership, and does
    the add-then-delete as one call. New `WatchlistServiceTest` cases cover the non-owner-succeeds path, the
    series-type rejection, and the missing-entry case.
  - Verified against the real running app: inserted a watchlist entry owned by one member (camila) directly, then
    used a *different* logged-in member's (admin's) session to move it to a meeting through the UI — confirmed via
    the database that the watchlist entry was deleted and the movie landed on the target meeting attributed to the
    acting member (admin), not the original owner.
- [x] Put the watch-link input on its own line below (currently cramped next to another field in the add/edit form)
  - `MovieSection`'s add-movie form had the title/IMDB-id field, the watch-link field, and the Add button all in
    one `Stack direction="row"`, cramped even on a phone-width screen. Now the primary field is its own row, with
    watch-link + Add on the row below it. The edit-details form's own watch-link field was already inside a
    `flexWrap` row, so it wasn't touched.
- [x] On the meeting page, add an "Add" button that lets you choose movie or series and then follow that specific flow
  - The Movies/Episodes sections' own add-movie and assign-episode forms used to be always visible under their
    headings, taking up space even when nothing was being added. Added a small "Add: Movie / Series" toggle
    (`MeetingDetailPage`, a `ToggleButtonGroup`) above both sections; `MovieSection`/`EpisodeSection` each gained a
    `showAddForm` prop and now only render their existing add form when the matching choice is selected (clicking
    the same choice again collapses it, same exclusive-toggle-that-can-deselect pattern `MediaTypeFilterButtons`
    doesn't use but `ToggleButtonGroup` supports natively). Neither section's own list/lookup logic changed — this
    is purely about *when* the existing forms show, not a new add flow. The Episodes section's "Up next" quick-add
    chips stay always visible regardless, since they're a compact one-click shortcut, not form clutter.
  - Verified in a real browser: neither form shows by default, clicking "Movie" reveals only the movie form,
    clicking "Series" swaps to the episode form, clicking the active choice again collapses it. Confirmed the
    underlying flow still works end-to-end through the new toggle (searched and added a real movie via TMDB,
    appeared in the Movies list).
- [x] Check the export/share link on desktop
  - Verified with a real Playwright run against a desktop-shaped browser context (no `navigator.share`/`canShare`,
    same as an actual desktop browser lacking the file-sharing Web Share API): clicking a month's share icon
    correctly falls through to the plain-download path and produces a real, correctly-named
    (`{club name}-{month}.png`) 1080x1920 PNG with the month header and poster grid intact — no bug found, this
    was purely a verification pass.
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

- [x] Consider using a drag handle on phone, instead of the whole line — done together with the item above.
- [x] Make the rating box size dynamic (currently a fixed 34x18).
  - Clarified with the user first: not fixing a truncation bug (tested a double-digit rank, "11", in the old fixed
    34px box — it rendered fully, no clipping), but sizing the box to the *widest content the current scale could
    ever show*, applied consistently to every cell, rather than the two guessed constants (34/136) that only
    happened to fit the seeded default scales.
  - `InlineRatingEditor` now computes `maxContentLength` from `quality`/`sentiment`'s own option data — number of
    digits in `scale.options.length` for `number` mode, longest actual label for `description` mode (still 1 char
    on small screens, preserving the earlier acronym-fallback finding), floored at 1 for `none`/empty — and sizes
    the box to `calc(${maxContentLength * 2}ch + 20px)`. Every `InlineRatingEditor` instance in the table reads
    the same `scales`/`fillWith`, so they all compute the same width independently — cells stay consistent
    without lifting anything into a shared parent. Using `ch` (font-relative) instead of a raw px guess also means
    the box now scales with the user's browser font-size/zoom, which a raw pixel constant never did.
  - Verified in a real browser across scenarios: default 6-option scales render effectively unchanged in `number`/
    `none` modes; a scale temporarily bumped to 11 options widens just enough to fit "10"/"11" without clipping;
    `description` mode now sizes to the longest real label instead of a flat 136px (visibly wider when a scale has
    a long label like "Excepcional!", intentionally — the box no longer ellipsis-truncates a label that doesn't
    fit); mobile stayed unaffected (small-screen still collapses `description` to 1 character, matching the
    earlier "check the acronym fallback" finding).

- [x] Validate how simple we can make minimum metrics, such as response time and status code rates.
  - If simple/cheap, let's do it
  - Turned out genuinely cheap: Ktor's own `MicrometerMetrics` plugin (`ktor-server-metrics-micrometer`) plus a
    `PrometheusMeterRegistry` (`io.micrometer:micrometer-registry-prometheus:1.16.0`) gives per-route request
    count, status code, and response-time percentiles out of the box — no app code needed to compute any of it,
    just install the plugin and expose `registry.scrape()` at a new unauthenticated `GET /metrics` (same posture
    as the existing `/health`; there's no monitoring stack yet to route auth through, and request-rate/timing
    isn't user data). JVM metrics (heap, GC, class loading) come along for free with the same registry.
  - Verified against the real running app: logged in through a real browser session, then confirmed `/metrics`
    recorded `ktor_http_server_requests_seconds{method="POST",route="/auth/login",status="200",...}` and the
    `GET /clubs` calls (both the 401 before login and the 200 after) with real response-time quantiles, plus the
    JVM/process metrics sections.
  - Nothing is scraping this yet (no Prometheus/Grafana in this project's infra) — this is the minimum useful
    building block (structured, aggregatable metrics available on request) rather than a full observability setup,
    matching "minimum" in the ask.

- [ ] Member-color and language-preference PATCHes raise the same "one action per click" question the movie/episode
  rating endpoints already answered (a per-field PATCH rather than a full overwrite) — still unresolved.

- [ ] Rating-save code-review leftovers (the optimistic-update work itself shipped — see CLAUDE.md's RatingScale
  section; these are the lower-stakes findings from reviewing it, deliberately not folded in blind):
  - [x] Duplicate `patchMovieReview`/`patchEpisodeReview` (~17 lines each, differ only by collection/id field) —
    a shared generic helper would remove the duplication.
    - Extracted a shared `patchPickReview` (`MeetingsPage.tsx`) that takes the collection/pick-id/review
      differences as accessor callbacks (`getPicks`/`withPicks`/`matchPick`/`getReviews`/`withReviews`/
      `createIfMissing`) instead of duplicating the `findIndex`/`matchesCurrent`/`upsertReview` wiring twice.
      `patchMovieReview`/`patchEpisodeReview` are now thin call sites supplying just those accessors.
  - [x] `previous` is looked up via a second `pick.reviews.find(...)` scan in `handleSaveRating`, duplicating the
    `review` lookup already computed a few lines below for the same member's cell in the same render pass —
    trivial, bounded by club member count.
    - The real duplication was actually between `handleSaveQuality` and `handleSaveSentiment` themselves — each
      ran its own separate `pick.reviews.find(r => r.memberId === myMemberId)` for the *same* review. Both
      `MovieRow`/`EpisodeRow` now compute `myReview` once and both handlers read from it.
  - [ ] The capture-previous/optimistic-patch/rollback dance is hand-inlined separately in
    `MovieRow.handleSaveRating`/`EpisodeRow.handleSaveRating`, and `RatingForm.tsx`'s 4 call sites use a
    completely different, non-optimistic pattern — worth its own pass to extract one reusable optimistic-save
    hook. Deliberately not attempted here: `RatingForm.tsx`'s 4 call sites (Series/Season/Episode/MovieSection's
    combined `rate` endpoint) have a genuinely different shape than the split quality/sentiment PATCH pattern
    used here, and generalizing across both risks introducing a regression in code this pass didn't otherwise
    touch. Left as its own follow-up.
  - Verified in a real browser after the refactor: opened admin's own rating box for an already-rated movie
    ("The Artifice Girl"), changed the quality rating, confirmed the `PATCH .../review/quality` call succeeded
    (200) and the box updated immediately without a page reload, then changed it back to its original value and
    confirmed via the database that the original rating was restored exactly.

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
