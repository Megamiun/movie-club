import LiveTvIcon from '@mui/icons-material/LiveTv'
import MovieIcon from '@mui/icons-material/Movie'
import { IconButton, Stack } from '@mui/material'

export interface MediaTypeFilters {
  showMovies: boolean
  showEpisodes: boolean
}

/** Independent movies/series show-hide toggle -- each can be turned on or off on its own (unlike an exclusive
 * either/or picker), so both, either, or neither can be shown at once. Shared by `MeetingsPage` and `CalendarPage`
 * so both keep the exact same interaction. */
export function MediaTypeFilterButtons({
  filters,
  onChange,
  seriesLabel = 'series',
}: {
  filters: MediaTypeFilters
  onChange: (next: MediaTypeFilters) => void
  seriesLabel?: string
}) {
  return (
    <Stack direction="row" spacing={0.5}>
      <IconButton
        size="small"
        color={filters.showMovies ? 'primary' : 'default'}
        onClick={() => onChange({ ...filters, showMovies: !filters.showMovies })}
        title={filters.showMovies ? 'Hide movies' : 'Show movies'}
      >
        <MovieIcon fontSize="small" />
      </IconButton>
      <IconButton
        size="small"
        color={filters.showEpisodes ? 'primary' : 'default'}
        onClick={() => onChange({ ...filters, showEpisodes: !filters.showEpisodes })}
        title={filters.showEpisodes ? `Hide ${seriesLabel}` : `Show ${seriesLabel}`}
      >
        <LiveTvIcon fontSize="small" />
      </IconButton>
    </Stack>
  )
}
