import AddIcon from '@mui/icons-material/Add'
import { Alert, Box, Button, Link, Paper, Stack, TextField, Typography } from '@mui/material'
import { useState, type FormEvent } from 'react'
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
import { useAsync } from '../hooks/useAsync'
import type { ClubOutletContext } from '../layout/ClubOutletContext'
import { memberName } from '../utils/members'
import { ratingLabel } from '../utils/rating'
import { resolveTitle } from '../utils/title'

export function MeetingsPage() {
  const { club } = useOutletContext<ClubOutletContext>()
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

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Meetings
      </Typography>

      <AsyncState loading={loading} error={error}>
        {sorted.length === 0 ? (
          <Typography color="text.secondary">No meetings yet.</Typography>
        ) : (
          <Stack spacing={1}>
            {sorted.map((meeting) => (
              <MeetingCard key={meeting.id} meeting={meeting} club={club} scales={scales ?? []} onChange={reload} />
            ))}
          </Stack>
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

function MeetingCard({
  meeting,
  club,
  scales,
  onChange,
}: {
  meeting: MeetingWithPicks
  club: ClubOutletContext['club']
  scales: RatingScale[]
  onChange: () => void
}) {
  const hasPicks = meeting.movies.length > 0 || meeting.episodes.length > 0

  return (
    <Paper variant="outlined" sx={{ p: 1.5 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'baseline' }}>
        <Link component={RouterLink} to={`/meetings/${meeting.id}`} underline="hover" sx={{ fontWeight: 600 }}>
          {meeting.date}
        </Link>
        <Typography variant="body2" color="text.secondary">
          {meeting.assignedMemberId ? memberName(club.members, meeting.assignedMemberId) : 'Shared / merged'}
        </Typography>
      </Stack>

      {!hasPicks && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          Nothing picked yet
        </Typography>
      )}

      <Stack spacing={0.75} sx={{ mt: hasPicks ? 1 : 0 }}>
        {meeting.movies.map((pick) => (
          <MoviePickRow key={pick.movie.id} pick={pick} club={club} scales={scales} onChange={onChange} />
        ))}
        {meeting.episodes.map((pick) => (
          <EpisodePickRow key={pick.episode.id} pick={pick} scales={scales} onChange={onChange} />
        ))}
      </Stack>
    </Paper>
  )
}

function MoviePickRow({
  pick,
  club,
  scales,
  onChange,
}: {
  pick: MeetingMoviePick
  club: ClubOutletContext['club']
  scales: RatingScale[]
  onChange: () => void
}) {
  const { movie, myQualityOptionId, mySentimentOptionId } = pick
  const [error, setError] = useState<string | null>(null)

  const details = [
    movie.year,
    movie.director,
    movie.runtimeMinutes ? `${movie.runtimeMinutes}min` : null,
    movie.genre && movie.genre.length > 0 ? movie.genre.join(', ') : null,
    movie.productionCountries && movie.productionCountries.length > 0 ? movie.productionCountries.join(', ') : null,
    ratingLabel(movie),
  ]
    .filter(Boolean)
    .join(' · ')

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
    <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
      <Typography variant="body2" sx={{ fontWeight: 500 }}>
        {resolveTitle(movie, club)}
      </Typography>
      <ImdbLink imdbId={movie.imdbId} />
      {details && (
        <Typography variant="body2" color="text.secondary">
          {details}
        </Typography>
      )}
      <InlineRatingEditor
        scales={scales}
        qualityOptionId={myQualityOptionId}
        sentimentOptionId={mySentimentOptionId}
        onSave={handleSaveRating}
      />
      {error && (
        <Typography variant="caption" color="error">
          {error}
        </Typography>
      )}
    </Stack>
  )
}

function EpisodePickRow({
  pick,
  scales,
  onChange,
}: {
  pick: MeetingEpisodePick
  scales: RatingScale[]
  onChange: () => void
}) {
  const { episode, myQualityOptionId, mySentimentOptionId } = pick
  const [error, setError] = useState<string | null>(null)

  const details = [
    episode.airDate,
    episode.director,
    episode.runtimeMinutes ? `${episode.runtimeMinutes}min` : null,
    episode.tmdbRating ? `TMDB ${episode.tmdbRating}` : null,
  ]
    .filter(Boolean)
    .join(' · ')

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
    <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
      <Typography variant="body2" sx={{ fontWeight: 500 }}>
        Ep. {episode.number}
        {episode.title ? ` — ${episode.title}` : ''}
      </Typography>
      {details && (
        <Typography variant="body2" color="text.secondary">
          {details}
        </Typography>
      )}
      <InlineRatingEditor
        scales={scales}
        qualityOptionId={myQualityOptionId}
        sentimentOptionId={mySentimentOptionId}
        onSave={handleSaveRating}
      />
      {error && (
        <Typography variant="caption" color="error">
          {error}
        </Typography>
      )}
    </Stack>
  )
}
