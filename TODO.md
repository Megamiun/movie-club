# TODO

- [x] Auto redirect from register page when already logged in
  - `RegisterPage` now redirects to `/clubs` (`<Navigate replace>`) as soon as `useAuth().member` is set, before
    rendering the form -- same early-return-after-hooks shape as the rest of the route tree. Verified in a real
    browser (Playwright against the dev server): a logged-out visit still renders the form, a logged-in visit to
    `/register` lands on `/clubs` immediately.
- [x] Add a tab with a per month view, where to show the posters
  - New "Calendar" tab (`CalendarPage`, `/clubs/{clubId}/calendar`) alongside Meetings/Movies/Series/Watchlist/
    Import/Overview in `ClubLayout` -- same underlying `meetingsApi.list` data as the Meetings table, same
    newest-first year-tab pattern, but grouped by calendar month within the selected year and rendered as a poster
    grid (one card per pick, not per meeting, so a merged meeting's multiple movies each get their own card) instead
    of table rows. Reuses the `posterUrl` plumbing added for the meeting detail page's poster (see below): a movie
    card shows `movie.posterUrl`; an episode card falls back to its parent series' own `posterUrl` (already embedded
    on the pick via `pick.series` -- no extra fetch needed) since an episode has no poster of its own. A pick with
    no resolved poster shows a movie/TV icon placeholder rather than leaving a gap. Every card links to its
    meeting's detail page.
  - Follow-up (user feedback): added a Movies/Series selector next to the "Calendar" heading -- first built as an
    exclusive either/or picker, but per follow-up feedback ("not exclusive, should be like main tab") changed to the
    same independent show/hide toggle the Meetings table already uses (each can be on/off on its own; both, either,
    or neither can be shown). That button pair was extracted out of `MeetingsPage` into a shared
    `MediaTypeFilterButtons` component (`frontend/src/components/MediaTypeFilterButtons.tsx`) so both pages render
    the identical control instead of two copies -- `MeetingsPage`'s own local version was deleted in favor of it.
    Defaults to movies only (unlike Meetings' movies-and-episodes-both default), persisted to `localStorage`
    (`movieclub.calendarMediaFilters`) like every other personal display preference in this app. Episode cards'
    titles now also include the episode's own name (e.g. "Twin Peaks S3E01 - Part 1"), not just the series title
    and S#E# code.
  - Verified in a real browser: tab renders, months group correctly with real poster art, episode cards visibly
    share their series' poster across every episode of that series, clicking a card navigates to the right meeting,
    the Movies/Series toggle filters correctly and its choice persists across reload.
  - Code review of this addition found one real duplication, fixed: the "default to current year, else most recent
    year with meetings" year-tab algorithm was copy-pasted verbatim from `MeetingsPage`. Extracted into a shared
    `useYearTabs` hook (`frontend/src/hooks/useYearTabs.ts`); both pages now call it instead of each carrying their
    own copy, so a future fix to that rule can't silently apply to only one of the two pages. Re-verified both
    pages afterward (Meetings' year tabs/default-year/scroll-to-today still work, Calendar still navigates
    correctly) since this touched state both pages depend on.
- [x] Show only movies in the home page by default
  - `MeetingsPage` (the club's default landing tab) already had a `showMovies`/`showEpisodes` filter, both
    previously defaulting to shown. Flipped `showEpisodes`'s default to `false` -- still a personal,
    `localStorage`-persisted preference, so anyone who wants episodes visible can turn the toggle back on and it
    sticks.
- [x] Remove date header, put it into the movie line
  - The meetings table used to give every meeting its own full-width header row (date + assigned member) above its
    pick rows, even for the common one-movie week. `MeetingRows` now only renders that wide row (`MeetingDropRow`)
    for a meeting with nothing visible to show (no picks at all, or everything filtered out) -- it's still the only
    row that exists in that case, and the only drop target left once there's no pick row to double as one. Once a
    meeting has any visible picks, the date rides along on the block's own first line instead: the first movie row,
    or the first episode group's series-label row, or (the rare case of an episode with no resolved series to hang
    a label row off of) the first episode row itself -- a `blockHeader` prop threaded into `MovieRow`/`EpisodeRow`
    renders it as a small caption above the title, plus a top border to still visually separate consecutive
    meetings' blocks. `registerRow` (used for the "scroll today's meeting into view" effect) moves to whichever row
    is now carrying the block's identity.
  - Verified in a real browser across all four shapes: movies-only default view (date above each movie title, no
    header rows at all), a meeting with both a movie and episodes (date only on the movie, the episode group below
    it stays unlabeled-by-date since it's the same meeting), episodes-only view (date merges into the series-label
    row), and a meeting with nothing picked (falls back to the old full header row, "Nothing picked yet"/"Hidden by
    filters" messaging intact). Drag-and-drop wasn't exercised through the browser automation this pass (Playwright
    synthetic pointer events didn't reliably trigger dnd-kit's `PointerSensor`) -- reasoned through instead: the
    change only omits the header row's own (now-redundant, since a populated meeting's pick rows already declare
    their own droppable zones) drop target for populated meetings, and merges one extra non-drag ref into the first
    row's existing `useForkRef` chain, without touching `useDraggable`/`useDroppable` setup itself.
  - Follow-up (user feedback: "make the date a diff column"): the date moved out of the Title cell's inline caption
    into its own dedicated leading `Date` table column (before `By`), still populated only on a meeting block's
    first row/label row and blank elsewhere -- same one-cell-per-block placement as before, just its own column
    instead of overlaid text. `MeetingDropRow` and the special first-episode-group-label row both split into a
    `Date` cell plus a `colSpan={columnCount - 1}` cell for the rest, so the "Hidden by filters"/series-label text
    still spans the remaining width. `columnCount` bumped from `9 + members` to `10 + members` to account for the
    new column. Re-verified all four shapes again in a real browser after this change.
  - Follow-up (user feedback): the Date column's text is now always bold (`fontWeight: 600`) in `MovieRow`/
    `EpisodeRow`, not just in the empty-meeting fallback row where it already was -- consistent weight regardless
    of which row shape is carrying the date for that block.
- [x] When clicking movie name, open meeting details
- [x] Add link to imdb as a link icon after title
  - Companion changes, same rows: the meetings table's title cell used to wrap the whole title in the IMDB link
    itself (`<ImdbLink variant="text">`), so clicking the name only ever opened IMDB. Split it: the title is now a
    plain `RouterLink` to `/meetings/{meetingId}` (same route the date-header link already used), with `ImdbLink`'s
    icon variant placed right after it as its own separate click target -- both `stopPropagation` on click, same as
    the existing director-name IMDB link already did, so neither fights the row's own drag-and-drop listeners.
    Applies to both `MovieRow` and `EpisodeRow`. Verified in a real browser: clicking a title navigates to the
    meeting detail page (`Meeting — {date}` heading); clicking the IMDB icon opens IMDB in a new tab without
    navigating the current page.
- [x] Add poster and all details into the meeting details page
  - The meeting detail page's Movie section already showed director/runtime/genre/country/rating -- the poster was
    the one missing piece, and there was no `posterUrl` anywhere on the `Movie`/`Series` API response to render (the
    existing `posterS3Key` column is long-unused/always null, see CLAUDE.md's MediaItem section). Added `posterUrl`
    to `MovieRow`/`SeriesRow` (and their routing responses), sourced by left-joining `MediaItems` through the
    catalog row's existing `media_item_id` -- the same join shape already used for `director`/`creator` via
    `People`. `MovieSection`'s accordion now shows a small poster thumbnail in the collapsed row and a larger one
    next to the details in the expanded view. Episode posters (via the parent series) are out of scope for this
    pass -- `EpisodeSection` doesn't currently fetch the parent series at all, so wiring that up is a bigger,
    separate change.
  - New repository integration tests (`findById resolves posterUrl through the linked MediaItem` /
    `... has a null posterUrl when there is no linked MediaItem`) for both Movie and Series. Verified visually
    against a real running backend + seeded data (Playwright screenshot of an expanded movie accordion showing the
    poster).

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
- [x] Separately: `PUT /movies/{id}/review` (and the episode equivalent) was a full overwrite of both quality *and*
  sentiment together, not independent per-field — `InlineRatingEditor` had to read the untouched field back out of
  its own props to avoid clobbering it on every save.
  - Fixed for Movie and Episode (the two entities the meetings table actually rates inline): `PATCH
    /movies/{movieId}/review/quality` and `.../review/sentiment` (and the `/episodes/...` equivalents),
    `MovieService`/`EpisodeService.rateQuality`/`rateSentiment`, backed by new `MovieRepository`/
    `EpisodeRepository.updateReviewQuality`/`updateReviewSentiment`. `InlineRatingEditor`'s `onSave` prop split into
    `onSaveQuality`/`onSaveSentiment`, each firing its own independent PATCH — no more echoing the other field or
    the comment back just to avoid wiping it. Series/Season's own combined `rate` endpoint is untouched; nothing in
    the UI edits their ratings inline the way Movie/Episode's meetings-table cells do, so splitting them wasn't in
    scope here.
  - Code review of this change found one real bug, fixed: the new repository methods used a check-then-act
    (`findReview` then `insert`/`update`) against `MemberMovieReviews`/`MemberEpisodeReviews`, both keyed by a
    composite primary key. Harmless as long as a rating save was one combined PUT, but the frontend now fires
    quality and sentiment as two independent, unsequenced PATCH calls — a first-time rating of both in quick
    succession could have both transactions see "no review yet" and both attempt an `insert`, the loser hitting a
    bare 500 off the PK violation and silently dropping that field's save despite the optimistic UI already
    showing it as persisted. Fixed by using Exposed's atomic `upsert` (`INSERT ... ON CONFLICT`) instead, with
    `onUpdate` assigning only the one field each method owns so the other field/`comment` are never touched by the
    conflict path.
  - Member-color and language-preference PATCHes raised the same "one action per click" question — still
    unresolved, out of scope for this pass.

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
