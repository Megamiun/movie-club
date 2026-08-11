# TODO

- [x] Update instantly when changing languages, colors, rating and so on, but just the relevant components
  - Language-preference edits now refresh the shared `club` object (`LanguagePreferencesSection` calls the outlet's
    `silentReload`, same pattern as member color); `ClubLayout` polls that `club` fetch every 15s; `MeetingsPage`/
    `MeetingDetailPage`/`SeriesDetailPage`/`SeasonDetailPage` each fold their rating-scales fetch's `silentReload`
    into their existing poll. See CLAUDE.md's RatingScale section.
- [ ] Make color selector more inclusive, should allow for a certain range of colors. Grill me
- [ ] Let a MediaItem be linked back to its real underlying Movie/Series catalog row, and used as that type — right
  now MediaItem only carries a flat `title` (no `translations`/`originalLanguage`), so anything holding just a
  MediaItem (Watchlist today) can't resolve a proper display title. `resolveTitle` isn't callable on Watchlist
  entries as a result — see CLAUDE.md's Movie section. Movie/Series already point *to* MediaItem
  (`media_item_id`); this is the reverse lookup (e.g. `MovieRepository`/`SeriesRepository.findByMediaItemId`,
  composed in `WatchlistService`, same as any other cross-entity orchestration).
- [ ] Validate how simple we can make minimum metrics, such as response time and status code rates. 
  - If simple/cheap, let's do it
- [ ] Make APIs do one action per click, such as when updating the sentiment, only patch the sentiment, same for quality
  - [ ] Ratings
  - [ ] Colors
  - [ ] Languages

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
