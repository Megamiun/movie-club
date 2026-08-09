import AddIcon from '@mui/icons-material/Add'
import {
  Alert,
  Box,
  Button,
  Link,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { Fragment, useState, type FormEvent } from 'react'
import { Link as RouterLink, useOutletContext } from 'react-router-dom'
import { meetingsApi } from '../api/meetings'
import { ApiError } from '../api/client'
import type { Episode, Movie, MeetingWithPicks } from '../api/types'
import { AsyncState } from '../components/AsyncState'
import { ImdbLink } from '../components/ImdbLink'
import { MemberAutocomplete } from '../components/MemberAutocomplete'
import { useAsync } from '../hooks/useAsync'
import type { ClubOutletContext } from '../layout/ClubOutletContext'
import { memberName } from '../utils/members'
import { ratingLabel } from '../utils/rating'
import { resolveTitle } from '../utils/title'

export function MeetingsPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const { data: meetings, loading, error, reload } = useAsync(() => meetingsApi.list(club.id), [club.id])
  const [date, setDate] = useState('')
  const [assignedMemberId, setAssignedMemberId] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitError(null)
    try {
      await meetingsApi.create(club.id, date, assignedMemberId ?? undefined)
      setDate('')
      setAssignedMemberId(null)
      reload()
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const sorted = [...(meetings ?? [])].sort((a, b) => a.date.localeCompare(b.date))

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Meetings
      </Typography>

      <AsyncState loading={loading} error={error}>
        {sorted.length === 0 ? (
          <Typography color="text.secondary">No meetings yet.</Typography>
        ) : (
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Date</TableCell>
                  <TableCell>Assigned</TableCell>
                  <TableCell>Title</TableCell>
                  <TableCell>Year</TableCell>
                  <TableCell>Director</TableCell>
                  <TableCell>Runtime</TableCell>
                  <TableCell>Genre</TableCell>
                  <TableCell>Country</TableCell>
                  <TableCell>Rating</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {sorted.map((meeting) => (
                  <MeetingRows key={meeting.id} meeting={meeting} club={club} />
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </AsyncState>

      <Box component="form" onSubmit={handleCreate} sx={{ mt: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          New meeting
        </Typography>
        {submitError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {submitError}
          </Alert>
        )}
        <Stack direction="row" spacing={1}>
          <TextField
            label="Date"
            type="date"
            size="small"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
            required
          />
          <MemberAutocomplete
            members={club.members}
            value={assignedMemberId}
            onChange={setAssignedMemberId}
            label="Assigned member (optional)"
          />
          <Button type="submit" variant="contained" startIcon={<AddIcon />}>
            Create
          </Button>
        </Stack>
      </Box>
    </Box>
  )
}

function MeetingRows({ meeting, club }: { meeting: MeetingWithPicks; club: ClubOutletContext['club'] }) {
  const hasPicks = meeting.movies.length > 0 || meeting.episodes.length > 0

  return (
    <Fragment>
      <TableRow sx={{ '& td': { bgcolor: 'action.hover', fontWeight: 500 } }}>
        <TableCell>
          <Link component={RouterLink} to={`/meetings/${meeting.id}`} underline="hover">
            {meeting.date}
          </Link>
        </TableCell>
        <TableCell>
          {meeting.assignedMemberId ? memberName(club.members, meeting.assignedMemberId) : 'Shared / merged'}
        </TableCell>
        <TableCell colSpan={7} sx={{ fontWeight: 400, color: 'text.secondary' }}>
          {!hasPicks && 'Nothing picked yet'}
        </TableCell>
      </TableRow>
      {meeting.movies.map((movie) => (
        <MovieRow key={movie.id} movie={movie} club={club} />
      ))}
      {meeting.episodes.map((episode) => (
        <EpisodeRow key={episode.id} episode={episode} />
      ))}
    </Fragment>
  )
}

function MovieRow({ movie, club }: { movie: Movie; club: ClubOutletContext['club'] }) {
  return (
    <TableRow>
      <TableCell />
      <TableCell />
      <TableCell>
        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
          <span>{resolveTitle(movie, club)}</span>
          <ImdbLink imdbId={movie.imdbId} />
        </Stack>
      </TableCell>
      <TableCell>{movie.year ?? '—'}</TableCell>
      <TableCell>{movie.director ?? '—'}</TableCell>
      <TableCell>{movie.runtimeMinutes ? `${movie.runtimeMinutes}min` : '—'}</TableCell>
      <TableCell>{movie.genre && movie.genre.length > 0 ? movie.genre.join(', ') : '—'}</TableCell>
      <TableCell>
        {movie.productionCountries && movie.productionCountries.length > 0
          ? movie.productionCountries.join(', ')
          : '—'}
      </TableCell>
      <TableCell>{ratingLabel(movie) ?? '—'}</TableCell>
    </TableRow>
  )
}

function EpisodeRow({ episode }: { episode: Episode }) {
  return (
    <TableRow>
      <TableCell />
      <TableCell />
      <TableCell>
        Ep. {episode.number}
        {episode.title ? ` — ${episode.title}` : ''}
      </TableCell>
      <TableCell>{episode.airDate ?? '—'}</TableCell>
      <TableCell>{episode.director ?? '—'}</TableCell>
      <TableCell>{episode.runtimeMinutes ? `${episode.runtimeMinutes}min` : '—'}</TableCell>
      <TableCell>—</TableCell>
      <TableCell>—</TableCell>
      <TableCell>{episode.tmdbRating ? `TMDB ${episode.tmdbRating}` : '—'}</TableCell>
    </TableRow>
  )
}
