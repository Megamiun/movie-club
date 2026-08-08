import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import {
  Alert,
  Box,
  Button,
  IconButton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material'
import { useState, type FormEvent } from 'react'
import { useOutletContext } from 'react-router-dom'
import { moviesApi } from '../api/movies'
import { seriesApi } from '../api/series'
import { watchlistApi } from '../api/watchlist'
import { ApiError } from '../api/client'
import type { ClubMember, TmdbSearchResult, WatchlistEntry } from '../api/types'
import { AsyncState } from '../components/AsyncState'
import { TmdbSearchAutocomplete } from '../components/TmdbSearchAutocomplete'
import { useAsync } from '../hooks/useAsync'
import type { ClubOutletContext } from '../layout/ClubOutletContext'
import { memberName } from '../utils/members'

const searchMoviesAndSeries = async (query: string): Promise<TmdbSearchResult[]> => {
  const [movies, series] = await Promise.all([moviesApi.search(query), seriesApi.search(query)])
  return [...movies, ...series]
}

export function WatchlistPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const { data: entries, loading, error, reload } = useAsync(() => watchlistApi.list(club.id), [club.id])
  const [addMode, setAddMode] = useState<'search' | 'manual'>('search')
  const [selectedResult, setSelectedResult] = useState<TmdbSearchResult | null>(null)
  const [title, setTitle] = useState('')
  const [imdbUrl, setImdbUrl] = useState('')
  const [notes, setNotes] = useState('')
  const [submitError, setSubmitError] = useState<string | null>(null)

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitError(null)
    try {
      if (addMode === 'search') {
        if (!selectedResult) return
        await watchlistApi.add(club.id, selectedResult.title, undefined, notes || undefined)
        setSelectedResult(null)
      } else {
        await watchlistApi.add(club.id, title, imdbUrl || undefined, notes || undefined)
        setTitle('')
        setImdbUrl('')
      }
      setNotes('')
      reload()
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Watchlist
      </Typography>

      <AsyncState loading={loading} error={error}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Title</TableCell>
              <TableCell>IMDB</TableCell>
              <TableCell>Notes</TableCell>
              <TableCell>Added by</TableCell>
              <TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {entries?.map((entry) => (
              <EntryRow key={entry.id} entry={entry} members={club.members} onChange={reload} />
            ))}
          </TableBody>
        </Table>
        {entries?.length === 0 && (
          <Typography color="text.secondary" sx={{ mt: 2 }}>
            No watchlist entries yet.
          </Typography>
        )}
      </AsyncState>

      <Box component="form" onSubmit={handleAdd} sx={{ mt: 3 }}>
        {submitError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {submitError}
          </Alert>
        )}
        <ToggleButtonGroup
          size="small"
          exclusive
          value={addMode}
          onChange={(_, mode) => mode && setAddMode(mode)}
          sx={{ mb: 1 }}
        >
          <ToggleButton value="search">Search by title</ToggleButton>
          <ToggleButton value="manual">Enter manually</ToggleButton>
        </ToggleButtonGroup>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
          {addMode === 'search' ? (
            <TmdbSearchAutocomplete
              search={searchMoviesAndSeries}
              value={selectedResult}
              onChange={setSelectedResult}
              label="Title"
            />
          ) : (
            <>
              <TextField label="Title" size="small" value={title} onChange={(e) => setTitle(e.target.value)} required />
              <TextField
                label="IMDB URL (optional)"
                size="small"
                value={imdbUrl}
                onChange={(e) => setImdbUrl(e.target.value)}
              />
            </>
          )}
          <TextField label="Notes (optional)" size="small" value={notes} onChange={(e) => setNotes(e.target.value)} />
          <Button type="submit" variant="contained" startIcon={<AddIcon />}>
            Add
          </Button>
        </Stack>
      </Box>
    </Box>
  )
}

function EntryRow({
  entry,
  members,
  onChange,
}: {
  entry: WatchlistEntry
  members: ClubMember[]
  onChange: () => void
}) {
  const [title, setTitle] = useState(entry.title)
  const [notes, setNotes] = useState(entry.notes ?? '')
  const [error, setError] = useState<string | null>(null)

  const handleBlurSave = async () => {
    if (title === entry.title && notes === (entry.notes ?? '')) return
    setError(null)
    try {
      await watchlistApi.update(entry.id, { title, notes: notes || undefined })
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

  return (
    <TableRow>
      <TableCell>
        <TextField
          variant="standard"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          onBlur={handleBlurSave}
        />
      </TableCell>
      <TableCell>
        {entry.imdbUrl ? (
          <a href={entry.imdbUrl} target="_blank" rel="noreferrer">
            link
          </a>
        ) : (
          '—'
        )}
      </TableCell>
      <TableCell>
        <TextField variant="standard" value={notes} onChange={(e) => setNotes(e.target.value)} onBlur={handleBlurSave} />
      </TableCell>
      <TableCell>{memberName(members, entry.memberId)}</TableCell>
      <TableCell align="right">
        {error && <Alert severity="error">{error}</Alert>}
        <IconButton size="small" onClick={handleDelete} title="Remove">
          <DeleteIcon fontSize="small" />
        </IconButton>
      </TableCell>
    </TableRow>
  )
}
