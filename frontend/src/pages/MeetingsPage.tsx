import AddIcon from '@mui/icons-material/Add'
import TuneIcon from '@mui/icons-material/Tune'
import {
  Alert,
  Box,
  Button,
  IconButton,
  Link,
  Paper,
  Popover,
  Slider,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableRow,
  Tabs,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material'
import { Fragment, useEffect, useState, type DragEvent, type FormEvent } from 'react'
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
import { useSeasonNumbers } from '../hooks/useSeasonNumbers'
import type { ClubOutletContext } from '../layout/ClubOutletContext'
import { useRatingDisplay, type RatingFillWith } from '../settings/RatingDisplayContext'
import { countryFlag, countryName } from '../utils/country'
import { formatDuration } from '../utils/duration'
import { episodeCode } from '../utils/episode'
import { memberName } from '../utils/members'
import { ratingLabel } from '../utils/rating'
import { resolveTitle } from '../utils/title'

/** Custom MIME type for dragging a movie/episode row between meeting groups in the table below -- namespaced so it
 * never collides with a browser's own drag types (e.g. dragging text/a link). */
const PICK_DRAG_TYPE = 'application/x-movieclub-pick'

interface PickDragPayload {
  kind: 'movie' | 'episode'
  id: string
  fromMeetingId: string
}

interface PickDropProps {
  onDragOver: (event: DragEvent) => void
  onDragLeave: () => void
  onDrop: (event: DragEvent) => void
}

export function MeetingsPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const { member } = useAuth()
  const { data: meetings, loading, error, reload, silentReload } = useAsync(() => meetingsApi.list(club.id), [club.id])
  const { data: scales } = useAsync(() => clubsApi.getRatingScales(club.id), [club.id])
  const [date, setDate] = useState('')
  const [assignedMemberId, setAssignedMemberId] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [selectedYear, setSelectedYear] = useState<string | null>(null)

  useEffect(() => {
    const interval = setInterval(silentReload, 5000)
    return () => clearInterval(interval)
  }, [silentReload])

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitError(null)
    try {
      await meetingsApi.create(club.id, date, assignedMemberId ?? undefined)
      setSelectedYear(date.slice(0, 4))
      setDate('')
      setAssignedMemberId(null)
      reload()
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const sorted = [...(meetings ?? [])].sort((a, b) => a.date.localeCompare(b.date))
  const columnCount = 8 + club.members.length
  const seasonNumbers = useSeasonNumbers(sorted.flatMap((meeting) => meeting.episodes.map((pick) => pick.episode.seasonId)))

  const years = [...new Set(sorted.map((meeting) => meeting.date.slice(0, 4)))].sort((a, b) => b.localeCompare(a))
  const currentYear = String(new Date().getFullYear())
  const defaultYear = years.includes(currentYear) ? currentYear : (years.at(0) ?? currentYear)
  const effectiveYear = selectedYear && years.includes(selectedYear) ? selectedYear : defaultYear
  const meetingsForYear = sorted.filter((meeting) => meeting.date.slice(0, 4) === effectiveYear)

  return (
    <Box>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <Typography variant="h5" gutterBottom sx={{ mb: 0 }}>
          Meetings
        </Typography>
        <RatingDisplaySettingsButton />
      </Stack>

      <AsyncState loading={loading} error={error}>
        {sorted.length === 0 ? (
          <Typography color="text.secondary">No meetings yet.</Typography>
        ) : (
          <>
            <Tabs
              value={effectiveYear}
              onChange={(_, year) => setSelectedYear(year)}
              sx={{ mb: 2 }}
            >
              {years.map((year) => (
                <Tab key={year} value={year} label={year} />
              ))}
            </Tabs>
            <TableContainer component={Paper} variant="outlined">
              <Table size="small" sx={{ '& .MuiTableCell-root': { px: 1.25 } }}>
                <TableBody>
                  {meetingsForYear.map((meeting) => (
                    <MeetingRows
                      key={meeting.id}
                      meeting={meeting}
                      club={club}
                      scales={scales ?? []}
                      myMemberId={member?.id ?? null}
                      columnCount={columnCount}
                      seasonNumbers={seasonNumbers}
                      onChange={silentReload}
                    />
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </>
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

function RatingDisplaySettingsButton() {
  const { gradientPercent, fillWith, setGradientPercent, setFillWith } = useRatingDisplay()
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null)

  return (
    <>
      <IconButton size="small" onClick={(e) => setAnchorEl(e.currentTarget)} title="Rating display settings">
        <TuneIcon fontSize="small" />
      </IconButton>
      <Popover
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      >
        <Stack spacing={2} sx={{ p: 2, width: 240 }}>
          <Box>
            <Typography variant="caption" color="text.secondary">
              Gradient blend ({gradientPercent}%)
            </Typography>
            <Slider
              size="small"
              value={gradientPercent}
              onChange={(_, value) => setGradientPercent(value as number)}
              min={0}
              max={50}
              step={5}
              marks
            />
          </Box>
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
              Fill with
            </Typography>
            <ToggleButtonGroup
              size="small"
              exclusive
              fullWidth
              value={fillWith}
              onChange={(_, value: RatingFillWith | null) => value && setFillWith(value)}
            >
              <ToggleButton value="number">Number</ToggleButton>
              <ToggleButton value="description">Description</ToggleButton>
              <ToggleButton value="none">No text</ToggleButton>
            </ToggleButtonGroup>
          </Box>
        </Stack>
      </Popover>
    </>
  )
}

function MeetingRows({
  meeting,
  club,
  scales,
  myMemberId,
  columnCount,
  seasonNumbers,
  onChange,
}: {
  meeting: MeetingWithPicks
  club: ClubOutletContext['club']
  scales: RatingScale[]
  myMemberId: string | null
  columnCount: number
  seasonNumbers: Map<string, number> | null
  onChange: () => void
}) {
  const hasPicks = meeting.movies.length > 0 || meeting.episodes.length > 0
  const [isDragOver, setIsDragOver] = useState(false)
  const [moveError, setMoveError] = useState<string | null>(null)

  const handleDragOver = (event: DragEvent) => {
    if (!event.dataTransfer.types.includes(PICK_DRAG_TYPE)) return
    event.preventDefault()
    setIsDragOver(true)
  }

  const handleDragLeave = () => setIsDragOver(false)

  const handleDrop = async (event: DragEvent) => {
    event.preventDefault()
    setIsDragOver(false)
    const raw = event.dataTransfer.getData(PICK_DRAG_TYPE)
    if (!raw) return
    const payload = JSON.parse(raw) as PickDragPayload
    if (payload.fromMeetingId === meeting.id) return

    setMoveError(null)
    try {
      if (payload.kind === 'movie') {
        await moviesApi.move(payload.id, meeting.id)
      } else {
        await episodesApi.unassignFromMeeting(payload.id, payload.fromMeetingId)
        await episodesApi.assignToMeeting(payload.id, meeting.id)
      }
      onChange()
    } catch (err) {
      setMoveError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const dropProps = { onDragOver: handleDragOver, onDragLeave: handleDragLeave, onDrop: handleDrop }

  return (
    <Fragment>
      <TableRow
        {...dropProps}
        sx={{ '& td': { bgcolor: isDragOver ? 'action.selected' : 'action.hover', fontWeight: 600 } }}
      >
        <TableCell colSpan={columnCount}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'baseline' }}>
            <Link component={RouterLink} to={`/meetings/${meeting.id}`} underline="hover">
              {meeting.date}
            </Link>
            <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 400 }}>
              {meeting.assignedMemberId ? memberName(club.members, meeting.assignedMemberId) : 'Shared / merged'}
              {!hasPicks && ' · Nothing picked yet'}
            </Typography>
            {moveError && (
              <Typography variant="caption" color="error">
                {moveError}
              </Typography>
            )}
          </Stack>
        </TableCell>
      </TableRow>
      {meeting.movies.map((pick) => (
        <MovieRow
          key={pick.movie.id}
          pick={pick}
          club={club}
          scales={scales}
          myMemberId={myMemberId}
          meetingId={meeting.id}
          dropProps={dropProps}
          onChange={onChange}
        />
      ))}
      {groupEpisodesBySeries(meeting.episodes).map((group) => (
        <Fragment key={group.series?.id ?? group.picks[0].episode.id}>
          {group.series && (
            <TableRow {...dropProps}>
              <TableCell colSpan={columnCount} sx={{ fontWeight: 600, color: 'text.secondary', border: 0, pb: 0 }}>
                {resolveTitle(group.series, club)}
              </TableCell>
            </TableRow>
          )}
          {group.picks.map((pick) => (
            <EpisodeRow
              key={pick.episode.id}
              pick={pick}
              club={club}
              scales={scales}
              myMemberId={myMemberId}
              meetingId={meeting.id}
              dropProps={dropProps}
              seasonNumber={seasonNumbers?.get(pick.episode.seasonId)}
              onChange={onChange}
            />
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
  meetingId,
  dropProps,
  onChange,
}: {
  pick: MeetingMoviePick
  club: ClubOutletContext['club']
  scales: RatingScale[]
  myMemberId: string | null
  meetingId: string
  dropProps: PickDropProps
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

  const handleDragStart = (event: DragEvent) => {
    const payload: PickDragPayload = { kind: 'movie', id: movie.id, fromMeetingId: meetingId }
    event.dataTransfer.setData(PICK_DRAG_TYPE, JSON.stringify(payload))
    event.dataTransfer.effectAllowed = 'move'
  }

  return (
    <TableRow draggable onDragStart={handleDragStart} {...dropProps} sx={{ cursor: 'grab' }}>
      <TableCell>
        <MemberBadge member={club.members.find((m) => m.memberId === movie.chosenById)} />
      </TableCell>
      <TableCell>
        <ImdbLink imdbId={movie.imdbId} variant="text">
          {resolveTitle(movie, club)}
        </ImdbLink>
        {error && (
          <Typography variant="caption" color="error" sx={{ display: 'block' }}>
            {error}
          </Typography>
        )}
      </TableCell>
      {club.members.map((clubMember) => {
        const review = pick.reviews.find((r) => r.memberId === clubMember.memberId)
        return (
          <TableCell key={clubMember.memberId}>
            <InlineRatingEditor
              scales={scales}
              memberName={clubMember.name}
              memberColor={clubMember.color}
              qualityOptionId={review?.qualityOptionId ?? null}
              sentimentOptionId={review?.sentimentOptionId ?? null}
              editable={clubMember.memberId === myMemberId}
              onSave={handleSaveRating}
            />
          </TableCell>
        )
      })}
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
      <TableCell align="right">{movie.runtimeMinutes ? formatDuration(movie.runtimeMinutes) : '—'}</TableCell>
      <TableCell>
        <TruncatedList items={movie.genre ?? []} maxChars={20} />
      </TableCell>
      <TableCell>
        <CountryFlags codes={movie.originCountry} />
      </TableCell>
      <TableCell>{ratingLabel(movie) ?? '—'}</TableCell>
    </TableRow>
  )
}

function EpisodeRow({
  pick,
  club,
  scales,
  myMemberId,
  meetingId,
  dropProps,
  seasonNumber,
  onChange,
}: {
  pick: MeetingEpisodePick
  club: ClubOutletContext['club']
  scales: RatingScale[]
  myMemberId: string | null
  meetingId: string
  dropProps: PickDropProps
  seasonNumber: number | undefined
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

  const handleDragStart = (event: DragEvent) => {
    const payload: PickDragPayload = { kind: 'episode', id: episode.id, fromMeetingId: meetingId }
    event.dataTransfer.setData(PICK_DRAG_TYPE, JSON.stringify(payload))
    event.dataTransfer.effectAllowed = 'move'
  }

  const rating = ratingLabel(episode) ?? (series ? ratingLabel(series) : null)
  const displayYear = episode.airDate ? episode.airDate.slice(0, 4) : (series?.year ?? null)

  return (
    <TableRow draggable onDragStart={handleDragStart} {...dropProps} sx={{ cursor: 'grab' }}>
      <TableCell>
        {series ? <MemberBadge member={club.members.find((m) => m.memberId === series.chosenById)} /> : '—'}
      </TableCell>
      <TableCell>
        {episode.imdbId || series ? (
          <ImdbLink imdbId={episode.imdbId ?? series!.imdbId} variant="text">
            {episodeCode(seasonNumber, episode.number)}
            {episode.title ? ` - ${episode.title}` : ''}
          </ImdbLink>
        ) : (
          <span>
            {episodeCode(seasonNumber, episode.number)}
            {episode.title ? ` - ${episode.title}` : ''}
          </span>
        )}
        {error && (
          <Typography variant="caption" color="error" sx={{ display: 'block' }}>
            {error}
          </Typography>
        )}
      </TableCell>
      {club.members.map((clubMember) => {
        const review = pick.reviews.find((r) => r.memberId === clubMember.memberId)
        return (
          <TableCell key={clubMember.memberId}>
            <InlineRatingEditor
              scales={scales}
              memberName={clubMember.name}
              memberColor={clubMember.color}
              qualityOptionId={review?.qualityOptionId ?? null}
              sentimentOptionId={review?.sentimentOptionId ?? null}
              editable={clubMember.memberId === myMemberId}
              onSave={handleSaveRating}
            />
          </TableCell>
        )
      })}
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
      <TableCell align="right">{episode.runtimeMinutes ? formatDuration(episode.runtimeMinutes) : '—'}</TableCell>
      <TableCell>
        <TruncatedList items={series?.genre ?? []} maxChars={20} />
      </TableCell>
      <TableCell>
        <CountryFlags codes={series?.originCountry} />
      </TableCell>
      <TableCell>{rating ?? '—'}</TableCell>
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
