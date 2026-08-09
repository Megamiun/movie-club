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
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { Fragment, useState, type FormEvent } from 'react'
import { Link as RouterLink, useOutletContext } from 'react-router-dom'
import { clubsApi } from '../api/clubs'
import { episodesApi } from '../api/series'
import { meetingsApi } from '../api/meetings'
import { moviesApi } from '../api/movies'
import { ApiError } from '../api/client'
import type { MeetingEpisodePick, MeetingMoviePick, MeetingWithPicks, RatingScale } from '../api/types'
import { AsyncState } from '../components/AsyncState'
import { ImdbLink } from '../components/ImdbLink'
import { InlineRatingEditor } from '../components/InlineRatingEditor'
import { MemberAutocomplete } from '../components/MemberAutocomplete'
import { useAuth } from '../auth/AuthContext'
import { useAsync } from '../hooks/useAsync'
import type { ClubOutletContext } from '../layout/ClubOutletContext'
import { memberName } from '../utils/members'
import { ratingLabel } from '../utils/rating'
import { resolveTitle } from '../utils/title'

export function MeetingsPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const { member } = useAuth()
  const { data: meetings, loading, error, reload } = useAsync(() => meetingsApi.list(club.id), [club.id])
  const { data: scales } = useAsync(() => clubsApi.getRatingScales(club.id), [club.id])
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
  const columnCount = 8 + club.members.length

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
              <TableBody>
                {sorted.map((meeting) => (
                  <MeetingRows
                    key={meeting.id}
                    meeting={meeting}
                    club={club}
                    scales={scales ?? []}
                    myMemberId={member?.id ?? null}
                    columnCount={columnCount}
                    onChange={reload}
                  />
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

function MeetingRows({
  meeting,
  club,
  scales,
  myMemberId,
  columnCount,
  onChange,
}: {
  meeting: MeetingWithPicks
  club: ClubOutletContext['club']
  scales: RatingScale[]
  myMemberId: string | null
  columnCount: number
  onChange: () => void
}) {
  const hasPicks = meeting.movies.length > 0 || meeting.episodes.length > 0

  return (
    <Fragment>
      <TableRow sx={{ '& td': { bgcolor: 'action.hover', fontWeight: 600 } }}>
        <TableCell colSpan={columnCount}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'baseline' }}>
            <Link component={RouterLink} to={`/meetings/${meeting.id}`} underline="hover">
              {meeting.date}
            </Link>
            <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 400 }}>
              {meeting.assignedMemberId ? memberName(club.members, meeting.assignedMemberId) : 'Shared / merged'}
              {!hasPicks && ' · Nothing picked yet'}
            </Typography>
          </Stack>
        </TableCell>
      </TableRow>
      {meeting.movies.map((pick) => (
        <MovieRow key={pick.movie.id} pick={pick} club={club} scales={scales} myMemberId={myMemberId} onChange={onChange} />
      ))}
      {groupEpisodesBySeries(meeting.episodes).map((group) => (
        <Fragment key={group.seriesTitle ?? group.picks[0].episode.id}>
          {group.seriesTitle && (
            <TableRow>
              <TableCell colSpan={columnCount} sx={{ fontWeight: 600, color: 'text.secondary', border: 0, pb: 0 }}>
                {group.seriesTitle}
              </TableCell>
            </TableRow>
          )}
          {group.picks.map((pick) => (
            <EpisodeRow key={pick.episode.id} pick={pick} club={club} scales={scales} myMemberId={myMemberId} onChange={onChange} />
          ))}
        </Fragment>
      ))}
    </Fragment>
  )
}

function groupEpisodesBySeries(episodes: MeetingEpisodePick[]) {
  const order: (string | null)[] = []
  const bySeriesTitle = new Map<string | null, MeetingEpisodePick[]>()
  for (const pick of episodes) {
    if (!bySeriesTitle.has(pick.seriesTitle)) {
      bySeriesTitle.set(pick.seriesTitle, [])
      order.push(pick.seriesTitle)
    }
    bySeriesTitle.get(pick.seriesTitle)!.push(pick)
  }
  return order.map((seriesTitle) => ({ seriesTitle, picks: bySeriesTitle.get(seriesTitle)! }))
}

function MovieRow({
  pick,
  club,
  scales,
  myMemberId,
  onChange,
}: {
  pick: MeetingMoviePick
  club: ClubOutletContext['club']
  scales: RatingScale[]
  myMemberId: string | null
  onChange: () => void
}) {
  const { movie } = pick
  const [error, setError] = useState<string | null>(null)

  const handleSaveRating = async (qualityOptionId?: string, sentimentOptionId?: string) => {
    setError(null)
    try {
      await moviesApi.rate(movie.id, qualityOptionId, sentimentOptionId)
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <TableRow>
      <TableCell>{memberName(club.members, movie.chosenById)}</TableCell>
      <TableCell>
        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
          <span>{resolveTitle(movie, club)}</span>
          <ImdbLink imdbId={movie.imdbId} />
        </Stack>
        {error && (
          <Typography variant="caption" color="error" display="block">
            {error}
          </Typography>
        )}
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
      {club.members.map((clubMember) => {
        const review = pick.reviews.find((r) => r.memberId === clubMember.memberId)
        return (
          <TableCell key={clubMember.memberId}>
            <InlineRatingEditor
              scales={scales}
              qualityOptionId={review?.qualityOptionId ?? null}
              sentimentOptionId={review?.sentimentOptionId ?? null}
              editable={clubMember.memberId === myMemberId}
              onSave={handleSaveRating}
            />
          </TableCell>
        )
      })}
    </TableRow>
  )
}

function EpisodeRow({
  pick,
  club,
  scales,
  myMemberId,
  onChange,
}: {
  pick: MeetingEpisodePick
  club: ClubOutletContext['club']
  scales: RatingScale[]
  myMemberId: string | null
  onChange: () => void
}) {
  const { episode } = pick
  const [error, setError] = useState<string | null>(null)

  const handleSaveRating = async (qualityOptionId?: string, sentimentOptionId?: string) => {
    setError(null)
    try {
      await episodesApi.rate(episode.id, qualityOptionId, sentimentOptionId)
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <TableRow>
      <TableCell>—</TableCell>
      <TableCell>
        Ep. {episode.number}
        {episode.title ? ` — ${episode.title}` : ''}
        {error && (
          <Typography variant="caption" color="error" display="block">
            {error}
          </Typography>
        )}
      </TableCell>
      <TableCell>{episode.airDate ?? '—'}</TableCell>
      <TableCell>{episode.director ?? '—'}</TableCell>
      <TableCell>{episode.runtimeMinutes ? `${episode.runtimeMinutes}min` : '—'}</TableCell>
      <TableCell>—</TableCell>
      <TableCell>—</TableCell>
      <TableCell>{episode.tmdbRating ? `TMDB ${episode.tmdbRating}` : '—'}</TableCell>
      {club.members.map((clubMember) => {
        const review = pick.reviews.find((r) => r.memberId === clubMember.memberId)
        return (
          <TableCell key={clubMember.memberId}>
            <InlineRatingEditor
              scales={scales}
              qualityOptionId={review?.qualityOptionId ?? null}
              sentimentOptionId={review?.sentimentOptionId ?? null}
              editable={clubMember.memberId === myMemberId}
              onSave={handleSaveRating}
            />
          </TableCell>
        )
      })}
    </TableRow>
  )
}

