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
  Typography,
} from '@mui/material'
import { useState, type FormEvent } from 'react'
import { useOutletContext } from 'react-router-dom'
import { watchlistApi } from '../api/watchlist'
import { ApiError } from '../api/client'
import type { WatchlistEntry } from '../api/types'
import { AsyncState } from '../components/AsyncState'
import { useAsync } from '../hooks/useAsync'
import type { ClubOutletContext } from '../layout/ClubOutletContext'

export function WatchlistPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const { data: entries, loading, error, reload } = useAsync(() => watchlistApi.list(club.id), [club.id])
  const [title, setTitle] = useState('')
  const [imdbUrl, setImdbUrl] = useState('')
  const [notes, setNotes] = useState('')
  const [submitError, setSubmitError] = useState<string | null>(null)

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitError(null)
    try {
      await watchlistApi.add(club.id, title, imdbUrl || undefined, notes || undefined)
      setTitle('')
      setImdbUrl('')
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
              <EntryRow key={entry.id} entry={entry} onChange={reload} />
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
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
          <TextField label="Title" size="small" value={title} onChange={(e) => setTitle(e.target.value)} required />
          <TextField
            label="IMDB URL (optional)"
            size="small"
            value={imdbUrl}
            onChange={(e) => setImdbUrl(e.target.value)}
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

function EntryRow({ entry, onChange }: { entry: WatchlistEntry; onChange: () => void }) {
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
      <TableCell sx={{ fontFamily: 'monospace' }}>{entry.memberId}</TableCell>
      <TableCell align="right">
        {error && <Alert severity="error">{error}</Alert>}
        <IconButton size="small" onClick={handleDelete} title="Remove">
          <DeleteIcon fontSize="small" />
        </IconButton>
      </TableCell>
    </TableRow>
  )
}
