import AddIcon from '@mui/icons-material/Add'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import DeleteIcon from '@mui/icons-material/Delete'
import { Alert, Box, Button, Chip, IconButton, Paper, Stack, TextField, Typography } from '@mui/material'
import { useState, type FormEvent } from 'react'
import { useOutletContext } from 'react-router-dom'
import { moviesApi } from '../api/movies'
import { seriesApi } from '../api/series'
import { watchlistApi } from '../api/watchlist'
import { ApiError } from '../api/client'
import type { ClubMember, TmdbSearchResult, WatchlistEntry } from '../api/types'
import { AsyncState } from '../components/AsyncState'
import { ImdbLink } from '../components/ImdbLink'
import { TmdbSearchAutocomplete } from '../components/TmdbSearchAutocomplete'
import { useAsync } from '../hooks/useAsync'
import type { ClubOutletContext } from '../layout/ClubOutletContext'
import { memberName } from '../utils/members'
import { ratingLabel } from '../utils/rating'

export function WatchlistPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const { data: entries, loading, error, reload } = useAsync(() => watchlistApi.list(club.id), [club.id])

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Watchlist
      </Typography>

      <AsyncState loading={loading} error={error}>
        <Stack spacing={4}>
          <WatchlistSection
            type="MOVIE"
            title="Movies"
            search={moviesApi.search}
            entries={entries?.filter((entry) => entry.type === 'MOVIE') ?? []}
            members={club.members}
            clubId={club.id}
            onChange={reload}
          />
          <WatchlistSection
            type="SERIES"
            title="Series"
            search={seriesApi.search}
            entries={entries?.filter((entry) => entry.type === 'SERIES') ?? []}
            members={club.members}
            clubId={club.id}
            onChange={reload}
          />
        </Stack>
      </AsyncState>
    </Box>
  )
}

function WatchlistSection({
  type,
  title,
  search,
  entries,
  members,
  clubId,
  onChange,
}: {
  type: 'MOVIE' | 'SERIES'
  title: string
  search: (query: string) => Promise<TmdbSearchResult[]>
  entries: WatchlistEntry[]
  members: ClubMember[]
  clubId: string
  onChange: () => void
}) {
  const [selectedResult, setSelectedResult] = useState<TmdbSearchResult | null>(null)
  const [notes, setNotes] = useState('')
  const [submitError, setSubmitError] = useState<string | null>(null)
  const sorted = [...entries].sort((a, b) => a.position - b.position)

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedResult) return
    setSubmitError(null)
    try {
      await watchlistApi.add(clubId, type, selectedResult.tmdbId, notes || undefined)
      setSelectedResult(null)
      setNotes('')
      onChange()
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        {title}
      </Typography>

      {sorted.length === 0 && (
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          Nothing here yet.
        </Typography>
      )}

      <Stack spacing={1} sx={{ mb: 2 }}>
        {sorted.map((entry, index) => (
          <WatchlistEntryCard
            key={entry.id}
            entry={entry}
            members={members}
            canMoveUp={index > 0}
            canMoveDown={index < sorted.length - 1}
            onChange={onChange}
          />
        ))}
      </Stack>

      {submitError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {submitError}
        </Alert>
      )}
      <Box component="form" onSubmit={handleAdd}>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
          <TmdbSearchAutocomplete
            search={search}
            value={selectedResult}
            onChange={setSelectedResult}
            label={`Search ${title.toLowerCase()}`}
          />
          <TextField label="Notes (optional)" size="small" value={notes} onChange={(e) => setNotes(e.target.value)} />
          <Button type="submit" variant="contained" startIcon={<AddIcon />}>
            Add
          </Button>
        </Stack>
      </Box>
    </Box>
  )
}

function WatchlistEntryCard({
  entry,
  members,
  canMoveUp,
  canMoveDown,
  onChange,
}: {
  entry: WatchlistEntry
  members: ClubMember[]
  canMoveUp: boolean
  canMoveDown: boolean
  onChange: () => void
}) {
  const [notes, setNotes] = useState(entry.notes ?? '')
  const [error, setError] = useState<string | null>(null)

  const handleBlurSave = async () => {
    if (notes === (entry.notes ?? '')) return
    setError(null)
    try {
      await watchlistApi.update(entry.id, notes || undefined)
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleMove = async (direction: 'UP' | 'DOWN') => {
    setError(null)
    try {
      await watchlistApi.move(entry.id, direction)
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleDelete = async () => {
    setError(null)
    try {
      await watchlistApi.remove(entry.id)
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const rating = ratingLabel(entry)

  return (
    <Paper variant="outlined" sx={{ p: 1.5, display: 'flex', gap: 1.5, alignItems: 'center' }}>
      {entry.posterUrl && (
        <Box component="img" src={entry.posterUrl} alt="" sx={{ width: 46, borderRadius: 0.5, flexShrink: 0 }} />
      )}
      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography sx={{ fontWeight: 500 }}>{entry.title}</Typography>
          {entry.year && <Chip size="small" label={entry.year} />}
          {rating && <Chip size="small" label={rating} />}
          <ImdbLink imdbId={entry.imdbId} />
        </Stack>
        <TextField
          variant="standard"
          placeholder="Notes"
          fullWidth
          size="small"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          onBlur={handleBlurSave}
        />
        <Typography variant="caption" color="text.secondary">
          Added by {memberName(members, entry.memberId)}
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mt: 1 }}>
            {error}
          </Alert>
        )}
      </Box>
      <Stack sx={{ flexShrink: 0 }}>
        <IconButton size="small" onClick={() => handleMove('UP')} disabled={!canMoveUp} title="Move up">
          <ArrowUpwardIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" onClick={() => handleMove('DOWN')} disabled={!canMoveDown} title="Move down">
          <ArrowDownwardIcon fontSize="small" />
        </IconButton>
      </Stack>
      <IconButton size="small" onClick={handleDelete} title="Remove" sx={{ flexShrink: 0 }}>
        <DeleteIcon fontSize="small" />
      </IconButton>
    </Paper>
  )
}
