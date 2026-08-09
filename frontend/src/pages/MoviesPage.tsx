import BookmarkAddIcon from '@mui/icons-material/BookmarkAdd'
import EventIcon from '@mui/icons-material/Event'
import { Alert, Box, Button, MenuItem, Select, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { meetingsApi } from '../api/meetings'
import { moviesApi } from '../api/movies'
import { watchlistApi } from '../api/watchlist'
import { ApiError } from '../api/client'
import type { TmdbSearchResult } from '../api/types'
import { TmdbSearchAutocomplete } from '../components/TmdbSearchAutocomplete'
import { useAsync } from '../hooks/useAsync'
import type { ClubOutletContext } from '../layout/ClubOutletContext'

export function MoviesPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const { data: meetings } = useAsync(() => meetingsApi.list(club.id), [club.id])
  const sortedMeetings = [...(meetings ?? [])].sort((a, b) => a.date.localeCompare(b.date))

  const [selectedResult, setSelectedResult] = useState<TmdbSearchResult | null>(null)
  const [targetMeetingId, setTargetMeetingId] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleAddToMeeting = async () => {
    if (!selectedResult || !targetMeetingId) return
    setError(null)
    try {
      await moviesApi.addByTmdbId(targetMeetingId, selectedResult.tmdbId)
      setMessage(`Added "${selectedResult.title}" to the meeting.`)
      setSelectedResult(null)
      setTargetMeetingId('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleAddToWatchlist = async () => {
    if (!selectedResult) return
    setError(null)
    try {
      await watchlistApi.add(club.id, 'MOVIE', selectedResult.tmdbId)
      setMessage(`Added "${selectedResult.title}" to the watchlist.`)
      setSelectedResult(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Movies
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Search TMDB for a movie and add it straight to a meeting or your watchlist, without opening a meeting first.
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      {message && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message}
        </Alert>
      )}

      <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', alignItems: 'center' }}>
        <TmdbSearchAutocomplete
          search={moviesApi.search}
          value={selectedResult}
          onChange={setSelectedResult}
          label="Search movies"
        />
        <Select
          size="small"
          displayEmpty
          value={targetMeetingId}
          onChange={(e) => setTargetMeetingId(e.target.value)}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">
            <em>Choose a meeting…</em>
          </MenuItem>
          {sortedMeetings.map((meeting) => (
            <MenuItem key={meeting.id} value={meeting.id}>
              {meeting.date}
            </MenuItem>
          ))}
        </Select>
        <Button
          variant="contained"
          startIcon={<EventIcon />}
          disabled={!selectedResult || !targetMeetingId}
          onClick={handleAddToMeeting}
        >
          Add to meeting
        </Button>
        <Button
          variant="outlined"
          startIcon={<BookmarkAddIcon />}
          disabled={!selectedResult}
          onClick={handleAddToWatchlist}
        >
          Add to watchlist
        </Button>
      </Stack>
    </Box>
  )
}
