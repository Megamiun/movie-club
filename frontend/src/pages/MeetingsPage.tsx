import AddIcon from '@mui/icons-material/Add'
import LiveTvIcon from '@mui/icons-material/LiveTv'
import MovieIcon from '@mui/icons-material/Movie'
import OpenInNewIcon from '@mui/icons-material/OpenInNew'
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
  TableHead,
  TableRow,
  Tabs,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material'
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  TouchSensor,
  pointerWithin,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { useForkRef } from '@mui/material/utils'
import { Fragment, useEffect, useRef, useState, type FormEvent } from 'react'
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
import { useSmartPolling } from '../hooks/useSmartPolling'
import { useSeasonNumbers, type SeasonCodeInfo } from '../hooks/useSeasonNumbers'
import type { ClubOutletContext } from '../layout/ClubOutletContext'
import { useRatingDisplay, type RatingFillWith } from '../settings/RatingDisplayContext'
import { countryFlag, countryName } from '../utils/country'
import { formatDuration } from '../utils/duration'
import { episodeCode } from '../utils/episode'
import { memberName } from '../utils/members'
import { ratingLabel } from '../utils/rating'
import { resolveTitle } from '../utils/title'

/** Data every draggable pick row carries -- [label] is precomputed at render time (the row already resolves its
 * own display title/code) so `DragOverlay` doesn't need a second lookup by id at the page level. */
interface PickDragData {
  kind: 'movie' | 'episode'
  fromMeetingId: string
  label: string
}

/** Every row within a meeting's block (the header row and each of its picks) registers as its own droppable, all
 * sharing the same [meetingId] -- reproduces the old native-DnD behavior of "drop anywhere within this meeting's
 * rows", not just its header. */
interface MeetingDropData {
  meetingId: string
}

/** Which pick types show in the meetings table -- a personal display preference like `RatingDisplayContext`
 * (persisted to `localStorage`, not club data), but plain component state rather than a shared context since
 * nothing outside this page's own component tree needs it. Both default to shown; movies were the only pick type
 * before series/episodes existed, so defaulting them on keeps today's view unchanged until a user actively hides
 * something. */
const MEETING_TYPE_FILTERS_KEY = 'movieclub.meetingTypeFilters'

interface MeetingTypeFilters {
  showMovies: boolean
  showEpisodes: boolean
}

function loadMeetingTypeFilters(): MeetingTypeFilters {
  try {
    const parsed = JSON.parse(localStorage.getItem(MEETING_TYPE_FILTERS_KEY) ?? '{}')
    return { showMovies: parsed.showMovies ?? true, showEpisodes: parsed.showEpisodes ?? true }
  } catch {
    return { showMovies: true, showEpisodes: true }
  }
}

export function MeetingsPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const { member } = useAuth()
  const { data: meetings, loading, error, reload, silentReload, setData: updateMeetings } = useAsync(() => meetingsApi.list(club.id), [club.id])
  const { data: scales, silentReload: silentReloadScales } = useAsync(() => clubsApi.getRatingScales(club.id), [club.id])
  const [date, setDate] = useState('')
  const [assignedMemberId, setAssignedMemberId] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [selectedYear, setSelectedYear] = useState<string | null>(null)
  const [typeFilters, setTypeFilters] = useState(loadMeetingTypeFilters)
  const [moveError, setMoveError] = useState<string | null>(null)
  const [activeDrag, setActiveDrag] = useState<PickDragData | null>(null)
  const [hoveredMeetingId, setHoveredMeetingId] = useState<string | null>(null)

  useSmartPolling(() => {
    silentReload()
    silentReloadScales()
  }, 10_000)

  useEffect(() => {
    localStorage.setItem(MEETING_TYPE_FILTERS_KEY, JSON.stringify(typeFilters))
  }, [typeFilters])

  // Optimistic rating saves -- patches the one review that changed directly in local state instead of waiting on
  // a save-then-refetch round trip (which used to refetch the club's *entire* meeting history just to show one
  // cell's new value). `handleSaveRating` in `MovieRow`/`EpisodeRow` calls these before awaiting the actual PUT,
  // then calls them again with the previous values to roll back on failure.
  const patchMovieReview = (meetingId: string, movieId: string, memberId: string, quality?: string, sentiment?: string) => {
    updateMeetings((prev) =>
      prev?.map((m) =>
        m.id !== meetingId ? m : {
          ...m,
          movies: m.movies.map((pick) =>
            pick.movie.id !== movieId ? pick : {
              ...pick,
              reviews: upsertReview(pick.reviews, memberId, quality, sentiment, () => ({
                movieId, memberId, qualityOptionId: null, sentimentOptionId: null, comment: null,
              })),
            },
          ),
        },
      ) ?? prev,
    )
  }

  const patchEpisodeReview = (meetingId: string, episodeId: string, memberId: string, quality?: string, sentiment?: string) => {
    updateMeetings((prev) =>
      prev?.map((m) =>
        m.id !== meetingId ? m : {
          ...m,
          episodes: m.episodes.map((pick) =>
            pick.episode.id !== episodeId ? pick : {
              ...pick,
              reviews: upsertReview(pick.reviews, memberId, quality, sentiment, () => ({
                episodeId, memberId, qualityOptionId: null, sentimentOptionId: null, comment: null,
              })),
            },
          ),
        },
      ) ?? prev,
    )
  }

  // PointerSensor (mouse/trackpad) needs some movement before a drag starts, so a plain click on a rating cell,
  // link, or button inside the row still passes through untouched. TouchSensor uses a short press-and-hold delay
  // instead of a distance threshold -- on touch, distance alone can't tell a drag apart from a scroll gesture in
  // time, but a delay can (a real drag holds still first; a scroll swipes immediately).
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 8 } }),
  )

  const handleDragStart = (event: DragStartEvent) => {
    setActiveDrag((event.active.data.current as PickDragData | undefined) ?? null)
  }

  const handleDragOver = (event: DragOverEvent) => {
    setHoveredMeetingId((event.over?.data.current as MeetingDropData | undefined)?.meetingId ?? null)
  }

  const handleDragEnd = async (event: DragEndEvent) => {
    setActiveDrag(null)
    setHoveredMeetingId(null)
    const { active, over } = event
    if (!over) return
    const data = active.data.current as PickDragData | undefined
    const targetMeetingId = (over.data.current as MeetingDropData | undefined)?.meetingId
    if (!data || !targetMeetingId || targetMeetingId === data.fromMeetingId) return

    setMoveError(null)
    try {
      if (data.kind === 'movie') {
        await moviesApi.move(String(active.id), targetMeetingId)
      } else {
        await episodesApi.unassignFromMeeting(String(active.id), data.fromMeetingId)
        await episodesApi.assignToMeeting(String(active.id), targetMeetingId)
      }
      silentReload()
    } catch (err) {
      setMoveError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

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
  const columnCount = 9 + club.members.length
  const seasonNumbers = useSeasonNumbers(sorted.flatMap((meeting) => meeting.episodes.map((pick) => pick.episode.seasonId)))

  const years = [...new Set(sorted.map((meeting) => meeting.date.slice(0, 4)))].sort((a, b) => b.localeCompare(a))
  const currentYear = String(new Date().getFullYear())
  const defaultYear = years.includes(currentYear) ? currentYear : (years.at(0) ?? currentYear)
  const effectiveYear = selectedYear && years.includes(selectedYear) ? selectedYear : defaultYear
  const meetingsForYear = sorted.filter((meeting) => meeting.date.slice(0, 4) === effectiveYear)

  const rowRefs = useRef(new Map<string, HTMLTableRowElement>())
  const focusedYearRef = useRef<string | null>(null)

  useEffect(() => {
    if (effectiveYear !== currentYear) return
    if (meetingsForYear.length === 0) return
    if (focusedYearRef.current === effectiveYear) return
    focusedYearRef.current = effectiveYear

    const today = new Date().toISOString().slice(0, 10)
    const target = meetingsForYear.find((meeting) => meeting.date >= today) ?? meetingsForYear.at(-1)
    const row = target && rowRefs.current.get(target.id)
    row?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  }, [effectiveYear, currentYear, meetingsForYear])

  return (
    <Box>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <Typography variant="h5" gutterBottom sx={{ mb: 0 }}>
          Meetings
        </Typography>
        <RatingDisplaySettingsButton />
        <MeetingTypeFilterButtons filters={typeFilters} onChange={setTypeFilters} />
      </Stack>

      {moveError && (
        <Alert severity="error" sx={{ mb: 1 }} onClose={() => setMoveError(null)}>
          {moveError}
        </Alert>
      )}

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
            <DndContext
              sensors={sensors}
              collisionDetection={pointerWithin}
              onDragStart={handleDragStart}
              onDragOver={handleDragOver}
              onDragEnd={handleDragEnd}
            >
              <TableContainer component={Paper} variant="outlined">
                <Table size="small" sx={{ '& .MuiTableCell-root': { py: 0.35, px: 1, fontSize: '0.8125rem' } }}>
                  <TableHead>
                    <TableRow sx={{ '& .MuiTableCell-root': { py: 0.5, fontWeight: 600, fontSize: '0.75rem', color: 'text.secondary', bgcolor: 'action.hover' } }}>
                      <TableCell width={36}>By</TableCell>
                      <TableCell>Title</TableCell>
                      {club.members.map((m) => (
                        <TableCell key={m.memberId} align="center" sx={{ px: 0.5 }}>
                          <Tooltip title={`Ratings by ${m.name}`}>
                            <Box sx={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
                              <MemberBadge member={m} />
                            </Box>
                          </Tooltip>
                        </TableCell>
                      ))}
                      <TableCell>Year</TableCell>
                      <TableCell>Director / Creator</TableCell>
                      <TableCell align="right">Runtime</TableCell>
                      <TableCell>Genre</TableCell>
                      <TableCell>Country</TableCell>
                      <TableCell>IMDb</TableCell>
                      <TableCell width={36} />
                    </TableRow>
                  </TableHead>
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
                        typeFilters={typeFilters}
                        isHovered={hoveredMeetingId === meeting.id}
                        onMovieRate={patchMovieReview}
                        onEpisodeRate={patchEpisodeReview}
                        registerRow={(el) => {
                          if (el) rowRefs.current.set(meeting.id, el)
                          else rowRefs.current.delete(meeting.id)
                        }}
                      />
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <DragOverlay>
                {activeDrag && (
                  <Paper elevation={3} sx={{ px: 1.5, py: 0.75, fontSize: '0.8125rem', fontWeight: 600 }}>
                    {activeDrag.label}
                  </Paper>
                )}
              </DragOverlay>
            </DndContext>
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

/** Two icon toggles -- Movies and Series -- for which pick types show in the table below. Each is independently
 * on/off (not exclusive: both, either, or neither can be active at once). */
function MeetingTypeFilterButtons({ filters, onChange }: { filters: MeetingTypeFilters; onChange: (next: MeetingTypeFilters) => void }) {
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
        title={filters.showEpisodes ? 'Hide series' : 'Show series'}
      >
        <LiveTvIcon fontSize="small" />
      </IconButton>
    </Stack>
  )
}

/** A meeting's header row is always a drop target (even for an empty meeting with no pick rows of its own to
 * double as one) -- registers its own [useDroppable] rather than relying on a pick row being present. */
function MeetingDropRow({
  meeting,
  columnCount,
  isHovered,
  hasAnyPicks,
  hasVisiblePicks,
  club,
  registerRow,
}: {
  meeting: MeetingWithPicks
  columnCount: number
  isHovered: boolean
  hasAnyPicks: boolean
  hasVisiblePicks: boolean
  club: ClubOutletContext['club']
  registerRow: (el: HTMLTableRowElement | null) => void
}) {
  const { setNodeRef } = useDroppable({ id: `drop-${meeting.id}-header`, data: { meetingId: meeting.id } satisfies MeetingDropData })
  const rowRef = useForkRef(setNodeRef, registerRow)

  return (
    <TableRow
      ref={rowRef}
      sx={{ '& td': { bgcolor: isHovered ? 'action.selected' : 'action.hover', fontWeight: 600 } }}
    >
      <TableCell colSpan={columnCount}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'baseline' }}>
          <Link component={RouterLink} to={`/meetings/${meeting.id}`} underline="hover">
            {meeting.date}
          </Link>
          <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 400 }}>
            {meeting.assignedMemberId ? memberName(club.members, meeting.assignedMemberId) : 'Shared / merged'}
            {!hasAnyPicks && ' · Nothing picked yet'}
            {hasAnyPicks && !hasVisiblePicks && ' · Hidden by filters'}
          </Typography>
        </Stack>
      </TableCell>
    </TableRow>
  )
}

function MeetingRows({
  meeting,
  club,
  scales,
  myMemberId,
  columnCount,
  seasonNumbers,
  typeFilters,
  isHovered,
  onMovieRate,
  onEpisodeRate,
  registerRow,
}: {
  meeting: MeetingWithPicks
  club: ClubOutletContext['club']
  scales: RatingScale[]
  myMemberId: string | null
  columnCount: number
  seasonNumbers: Map<string, SeasonCodeInfo> | null
  typeFilters: MeetingTypeFilters
  isHovered: boolean
  onMovieRate: (meetingId: string, movieId: string, memberId: string, quality?: string, sentiment?: string) => void
  onEpisodeRate: (meetingId: string, episodeId: string, memberId: string, quality?: string, sentiment?: string) => void
  registerRow: (el: HTMLTableRowElement | null) => void
}) {
  const hasAnyPicks = meeting.movies.length > 0 || meeting.episodes.length > 0
  const visibleMovies = typeFilters.showMovies ? meeting.movies : []
  const visibleEpisodeGroups = typeFilters.showEpisodes ? groupEpisodesBySeries(meeting.episodes) : []
  const hasVisiblePicks = visibleMovies.length > 0 || visibleEpisodeGroups.length > 0

  return (
    <Fragment>
      <MeetingDropRow
        meeting={meeting}
        columnCount={columnCount}
        isHovered={isHovered}
        hasAnyPicks={hasAnyPicks}
        hasVisiblePicks={hasVisiblePicks}
        club={club}
        registerRow={registerRow}
      />
      {visibleMovies.map((pick) => (
        <MovieRow
          key={pick.movie.id}
          pick={pick}
          club={club}
          scales={scales}
          myMemberId={myMemberId}
          meetingId={meeting.id}
          onRate={onMovieRate}
        />
      ))}
      {visibleEpisodeGroups.map((group) => (
        <Fragment key={group.series?.id ?? group.picks[0].episode.id}>
          {group.series && (
            <TableRow>
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
              seasonCode={seasonNumbers?.get(pick.episode.seasonId)}
              onRate={onEpisodeRate}
            />
          ))}
        </Fragment>
      ))}
    </Fragment>
  )
}

/** Replaces [memberId]'s review in [reviews] (preserving its other fields, e.g. `comment`) if one already exists,
 * otherwise appends a new one built from [createIfMissing] -- shared by `patchMovieReview`/`patchEpisodeReview`,
 * generic over `MovieReview`/`EpisodeReview` since they're identical shapes apart from the foreign-key field name. */
function upsertReview<R extends { memberId: string; qualityOptionId: string | null; sentimentOptionId: string | null }>(
  reviews: R[],
  memberId: string,
  quality: string | undefined,
  sentiment: string | undefined,
  createIfMissing: () => R,
): R[] {
  const qualityOptionId = quality ?? null
  const sentimentOptionId = sentiment ?? null
  if (!reviews.some((r) => r.memberId === memberId)) {
    return [...reviews, { ...createIfMissing(), qualityOptionId, sentimentOptionId }]
  }
  return reviews.map((r) => (r.memberId === memberId ? { ...r, qualityOptionId, sentimentOptionId } : r))
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
  onRate,
}: {
  pick: MeetingMoviePick
  club: ClubOutletContext['club']
  scales: RatingScale[]
  myMemberId: string | null
  meetingId: string
  onRate: (meetingId: string, movieId: string, memberId: string, quality?: string, sentiment?: string) => void
}) {
  const { movie } = pick
  const [error, setError] = useState<string | null>(null)
  const dragData: PickDragData = { kind: 'movie', fromMeetingId: meetingId, label: resolveTitle(movie, club) }
  const { attributes, listeners, setNodeRef: setDraggableRef, isDragging } = useDraggable({ id: movie.id, data: dragData })
  const { setNodeRef: setDroppableRef } = useDroppable({ id: `drop-${meetingId}-movie-${movie.id}`, data: { meetingId } satisfies MeetingDropData })
  const rowRef = useForkRef(setDraggableRef, setDroppableRef)

  // Optimistic: patches the review locally before the request even fires, so the box updates instantly instead of
  // waiting on a save-then-refetch round trip. Only ever called for the viewer's own column (see `editable` below),
  // so `myMemberId` is always set in practice here; the guard just covers the type, not a reachable UI state.
  const handleSaveRating = async (qualityOptionId?: string, sentimentOptionId?: string) => {
    if (!myMemberId) return
    const previous = pick.reviews.find((r) => r.memberId === myMemberId)
    setError(null)
    onRate(meetingId, movie.id, myMemberId, qualityOptionId, sentimentOptionId)
    try {
      await moviesApi.rate(movie.id, qualityOptionId, sentimentOptionId)
    } catch (err) {
      onRate(meetingId, movie.id, myMemberId, previous?.qualityOptionId ?? undefined, previous?.sentimentOptionId ?? undefined)
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <TableRow
      ref={rowRef}
      {...attributes}
      {...listeners}
      sx={{ cursor: 'grab', opacity: isDragging ? 0.4 : 1, touchAction: 'none' }}
    >
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
          ) : movie.director
        ) : '—' }
      </TableCell>
      <TableCell align="right">{movie.runtimeMinutes ? formatDuration(movie.runtimeMinutes) : '—'}</TableCell>
      <TableCell><TruncatedList items={movie.genre ?? []} maxChars={20} /></TableCell>
      <TableCell><CountryFlags codes={movie.originCountry} /></TableCell>
      <TableCell>{ratingLabel(movie) ?? '—'}</TableCell>
      <TableCell align="center"><WatchLinkCell href={movie.watchLink} /></TableCell>
    </TableRow>
  )
}

function EpisodeRow({
  pick,
  club,
  scales,
  myMemberId,
  meetingId,
  seasonCode,
  onRate,
}: {
  pick: MeetingEpisodePick
  club: ClubOutletContext['club']
  scales: RatingScale[]
  myMemberId: string | null
  meetingId: string
  seasonCode: SeasonCodeInfo | undefined
  onRate: (meetingId: string, episodeId: string, memberId: string, quality?: string, sentiment?: string) => void
}) {
  const { episode, series } = pick
  const [error, setError] = useState<string | null>(null)
  const label = `${episodeCode(seasonCode?.number, episode.number, seasonCode?.seasonDigits, seasonCode?.episodeDigits)}${episode.title ? ` - ${episode.title}` : ''}`
  const dragData: PickDragData = { kind: 'episode', fromMeetingId: meetingId, label }
  const { attributes, listeners, setNodeRef: setDraggableRef, isDragging } = useDraggable({ id: episode.id, data: dragData })
  const { setNodeRef: setDroppableRef } = useDroppable({ id: `drop-${meetingId}-episode-${episode.id}`, data: { meetingId } satisfies MeetingDropData })
  const rowRef = useForkRef(setDraggableRef, setDroppableRef)

  // Optimistic -- see `MovieRow.handleSaveRating` above for the rationale; same shape.
  const handleSaveRating = async (qualityOptionId?: string, sentimentOptionId?: string) => {
    if (!myMemberId) return
    const previous = pick.reviews.find((r) => r.memberId === myMemberId)
    setError(null)
    onRate(meetingId, episode.id, myMemberId, qualityOptionId, sentimentOptionId)
    try {
      await episodesApi.rate(episode.id, qualityOptionId, sentimentOptionId)
    } catch (err) {
      onRate(meetingId, episode.id, myMemberId, previous?.qualityOptionId ?? undefined, previous?.sentimentOptionId ?? undefined)
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const rating = ratingLabel(episode)
  const displayYear = episode.airDate ? episode.airDate.slice(0, 4) : (series?.year ?? null)

  return (
    <TableRow
      ref={rowRef}
      {...attributes}
      {...listeners}
      sx={{ cursor: 'grab', opacity: isDragging ? 0.4 : 1, touchAction: 'none' }}
    >
      <TableCell>
        {series ? <MemberBadge member={club.members.find((m) => m.memberId === series.chosenById)} /> : '—'}
      </TableCell>
      <TableCell>
        {episode.imdbId ? (
          <ImdbLink imdbId={episode.imdbId} variant="text">
            {label}
          </ImdbLink>
        ) : (
          <span>{label}</span>
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
      <TableCell align="center">—</TableCell>
    </TableRow>
  )
}

/** A plain icon-only link to a movie pick's optional "where to watch" URL (HBO/Netflix/magnet link/etc., see
 * `MovieSection`) -- episodes have no equivalent field, so `EpisodeRow` always renders the blank "—" fallback
 * directly rather than calling this. */
function WatchLinkCell({ href }: { href: string | null }) {
  if (!href) return <>—</>
  return (
    <Tooltip title={href}>
      <IconButton size="small" component="a" href={href} target="_blank" rel="noopener noreferrer">
        <OpenInNewIcon fontSize="inherit" />
      </IconButton>
    </Tooltip>
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
