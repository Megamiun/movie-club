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
  Tooltip,
  Typography,
} from '@mui/material'
import { Fragment, useState, type FormEvent } from 'react'
import { Link as RouterLink, useOutletContext } from 'react-router-dom'
import { clubsApi } from '../api/clubs'
import { episodesApi } from '../api/series'
import { meetingsApi } from '../api/meetings'
import { moviesApi } from '../api/movies'
import { ApiError } from '../api/client'
import type { MeetingEpisodePick, MeetingMoviePick, MeetingWithPicks, RatingScale, Series } from '../api/types'
import { AsyncState } from '../components/AsyncState'
import { ImdbLink } from '../components/ImdbLink'
import { InlineRatingEditor } from '../components/InlineRatingEditor'
import { MemberAutocomplete } from '../components/MemberAutocomplete'
import { MemberBadge } from '../components/MemberBadge'
import { TruncatedList } from '../components/TruncatedList'
import { useAuth } from '../auth/AuthContext'
import { useAsync } from '../hooks/useAsync'
import type { ClubOutletContext } from '../layout/ClubOutletContext'
import { countryFlag, countryName } from '../utils/country'
import { formatDuration } from '../utils/duration'
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
        <Fragment key={group.series?.id ?? group.picks[0].episode.id}>
          {group.series && (
            <TableRow>
              <TableCell colSpan={columnCount} sx={{ fontWeight: 600, color: 'text.secondary', border: 0, pb: 0 }}>
                {resolveTitle(group.series, club)}
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
  const bySeriesId = new Map<string | null, { series: Series | null; picks: MeetingEpisodePick[] }>()
  for (const pick of episodes) {
    const key = pick.series?.id ?? null
    if (!bySeriesId.has(key)) {
      bySeriesId.set(key, { series: pick.series, picks: [] })
      order.push(key)
    }
    bySeriesId.get(key)!.picks.push(pick)
  }
  return order.map((key) => bySeriesId.get(key)!)
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
      <TableCell>
        <MemberBadge member={club.members.find((m) => m.memberId === movie.chosenById)} />
      </TableCell>
      <TableCell>
        <ImdbLink imdbId={movie.imdbId} variant="text">
          {resolveTitle(movie, club)}
        </ImdbLink>
        {error && (
          <Typography variant="caption" color="error" display="block">
            {error}
          </Typography>
        )}
      </TableCell>
      <TableCell>{movie.year ?? '—'}</TableCell>
      <TableCell>
        {movie.director ? (
          movie.directorImdbId ? (
            <ImdbLink imdbId={movie.directorImdbId} kind="name" variant="text">
              {movie.director}
            </ImdbLink>
          ) : (
            movie.director
          )
        ) : (
          '—'
        )}
      </TableCell>
      <TableCell>{movie.runtimeMinutes ? formatDuration(movie.runtimeMinutes) : '—'}</TableCell>
      <TableCell>
        <TruncatedList items={movie.genre ?? []} />
      </TableCell>
      <TableCell>
        <CountryFlags codes={movie.originCountry} />
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
  const { episode, series } = pick
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

  const rating = episode.tmdbRating ? `TMDB ${episode.tmdbRating}` : series ? ratingLabel(series) : null
  const displayYear = episode.airDate ? episode.airDate.slice(0, 4) : (series?.year ?? null)

  return (
    <TableRow>
      <TableCell>
        {series ? <MemberBadge member={club.members.find((m) => m.memberId === series.chosenById)} /> : '—'}
      </TableCell>
      <TableCell>
        {series ? (
          <ImdbLink imdbId={series.imdbId} variant="text">
            Ep. {episode.number}
            {episode.title ? ` — ${episode.title}` : ''}
          </ImdbLink>
        ) : (
          <span>
            Ep. {episode.number}
            {episode.title ? ` — ${episode.title}` : ''}
          </span>
        )}
        {error && (
          <Typography variant="caption" color="error" display="block">
            {error}
          </Typography>
        )}
      </TableCell>
      <TableCell>
        {episode.airDate ? (
          <Tooltip title={episode.airDate}>
            <span>{displayYear}</span>
          </Tooltip>
        ) : (
          (displayYear ?? '—')
        )}
      </TableCell>
      <TableCell>
        {episode.director && episode.directorImdbId ? (
          <ImdbLink imdbId={episode.directorImdbId} kind="name" variant="text">
            {episode.director}
          </ImdbLink>
        ) : (
          (episode.director ?? series?.creator ?? '—')
        )}
      </TableCell>
      <TableCell>{episode.runtimeMinutes ? formatDuration(episode.runtimeMinutes) : '—'}</TableCell>
      <TableCell>
        <TruncatedList items={series?.genre ?? []} />
      </TableCell>
      <TableCell>
        <CountryFlags codes={series?.originCountry} />
      </TableCell>
      <TableCell>{rating ?? '—'}</TableCell>
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


function CountryFlags({ codes }: { codes: string[] | null | undefined }) {
  if (!codes || codes.length === 0) return <>—</>
  return (
    <Stack direction="row" spacing={0.5} component="span">
      {codes.map((code) => (
        <Tooltip key={code} title={countryName(code)}>
          <span>{countryFlag(code)}</span>
        </Tooltip>
      ))}
    </Stack>
  )
}
