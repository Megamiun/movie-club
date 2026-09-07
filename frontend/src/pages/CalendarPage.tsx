import IosShareIcon from '@mui/icons-material/IosShare'
import LiveTvIcon from '@mui/icons-material/LiveTv'
import MovieIcon from '@mui/icons-material/Movie'
import { Alert, Box, CircularProgress, IconButton, Stack, Tab, Tabs, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useOutletContext } from 'react-router-dom'
import { meetingsApi } from '../api/meetings'
import type { MeetingEpisodePick, MeetingMoviePick, MeetingWithPicks } from '../api/types'
import { AsyncState } from '../components/AsyncState'
import { MediaTypeFilterButtons, type MediaTypeFilters } from '../components/MediaTypeFilterButtons'
import { useAsync } from '../hooks/useAsync'
import { useContainerWidth } from '../hooks/useContainerWidth'
import { useSeasonNumbers } from '../hooks/useSeasonNumbers'
import { useSmartPolling } from '../hooks/useSmartPolling'
import { useYearTabs } from '../hooks/useYearTabs'
import type { ClubOutletContext } from '../layout/ClubOutletContext'
import { episodeCode } from '../utils/episode'
import { generateMonthShareImage } from '../utils/monthShareImage'
import { resolveTitle } from '../utils/title'

const MONTH_FORMATTER = new Intl.DateTimeFormat('en', { month: 'long', year: 'numeric' })

const POSTER_CARD_WIDTH = 120
const POSTER_GRID_GAP = 16 // matches the Box's `gap: 2` MUI spacing below

/** How many fixed-width poster cards fit per row at the given container width, then spreads the month's cards
 * evenly across however many rows that takes -- e.g. 7 cards at a 5-per-row width becomes 4+3 instead of a
 * naturally-wrapped 5+2, and a count that already fits in one row is never broken up at all. Returns `null` while
 * the container hasn't been measured yet, so the caller can fall back to plain CSS wrapping for that first paint
 * instead of flashing a single column. */
function balancedColumns(count: number, containerWidth: number): number | null {
  if (count === 0 || containerWidth <= 0) return null
  const maxPerRow = Math.max(1, Math.floor((containerWidth + POSTER_GRID_GAP) / (POSTER_CARD_WIDTH + POSTER_GRID_GAP)))
  if (count <= maxPerRow) return count
  const rows = Math.ceil(count / maxPerRow)
  return Math.ceil(count / rows)
}

/** Which pick types the poster grid shows -- same independent show/hide toggle as the Meetings table (each can be
 * on/off on its own), not an either/or picker. A personal display preference, `localStorage`-persisted like the
 * Meetings table's own filters; defaults to movies only, unlike Meetings' movies-and-episodes-both default, since a
 * poster grid mixing two unrelated things at once by default is less useful than the table's row-based view. */
const CALENDAR_MEDIA_FILTERS_KEY = 'movieclub.calendarMediaFilters'

function loadCalendarMediaFilters(): MediaTypeFilters {
  try {
    const parsed = JSON.parse(localStorage.getItem(CALENDAR_MEDIA_FILTERS_KEY) ?? '{}')
    return { showMovies: parsed.showMovies ?? true, showEpisodes: parsed.showEpisodes ?? false }
  } catch {
    return { showMovies: true, showEpisodes: false }
  }
}

interface PosterCardInfo {
  key: string
  meetingId: string
  date: string
  title: string
  posterUrl: string | null
  isEpisode: boolean
}

/** A visual, poster-first alternative to the Meetings table -- same underlying meeting data, grouped by calendar
 * month within the selected year instead of one row per pick. Episode cards fall back to their parent series'
 * poster (an episode has no poster of its own, see CLAUDE.md's MediaItem section) rather than going without art. */
export function CalendarPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const { data: meetings, loading, error, silentReload } = useAsync(() => meetingsApi.list(club.id), [club.id])
  useSmartPolling(silentReload, 5_000)
  const [mediaFilters, setMediaFilters] = useState(loadCalendarMediaFilters)
  const [sharingMonth, setSharingMonth] = useState<string | null>(null)
  const [shareError, setShareError] = useState<string | null>(null)
  const [gridRef, gridWidth] = useContainerWidth<HTMLDivElement>()

  useEffect(() => {
    localStorage.setItem(CALENDAR_MEDIA_FILTERS_KEY, JSON.stringify(mediaFilters))
  }, [mediaFilters])

  /** Generates that month's poster grid as a 1080x1920 PNG (Instagram Stories' own aspect ratio) and either hands
   * it to the OS share sheet (phones -- lets the member pick Instagram, Messages, etc. directly) or falls back to
   * a plain download (desktop, or any browser without file-sharing support). There's no server involvement at all
   * -- the image is drawn entirely client-side from the same poster URLs already on screen. */
  const handleShare = async (month: string, cards: PosterCardInfo[]) => {
    setShareError(null)
    setSharingMonth(month)
    try {
      const posterUrls = cards.map((card) => card.posterUrl).filter((url): url is string => Boolean(url))
      const label = MONTH_FORMATTER.format(new Date(`${month}-01T00:00:00`))
      const blob = await generateMonthShareImage(posterUrls, label)
      const filename = `${club.name.replace(/[^a-z0-9]+/gi, '-')}-${month}.png`
      const file = new File([blob], filename, { type: 'image/png' })

      if (navigator.canShare?.({ files: [file] })) {
        try {
          await navigator.share({ files: [file], title: `${club.name} — ${label}` })
          return
        } catch (err) {
          if (err instanceof Error && err.name === 'AbortError') return
          // Sharing failed for some other reason (e.g. no share target picked up the file type) -- fall through
          // to a plain download instead of leaving the member with nothing.
        }
      }

      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = filename
      link.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setShareError(err instanceof Error ? err.message : 'Something went wrong generating the image')
    } finally {
      setSharingMonth(null)
    }
  }

  const seasonNumbers = useSeasonNumbers(
    (meetings ?? []).flatMap((meeting) => meeting.episodes.map((pick) => pick.episode.seasonId)),
  )

  const { sorted, years, effectiveYear, itemsForYear: meetingsForYear, setSelectedYear } = useYearTabs(meetings ?? [])

  const months = groupByMonth(meetingsForYear)

  return (
    <Box>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
        <Typography variant="h5" gutterBottom sx={{ mb: 0 }}>
          Calendar
        </Typography>
        <MediaTypeFilterButtons filters={mediaFilters} onChange={setMediaFilters} />
      </Stack>

      {shareError && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setShareError(null)}>
          {shareError}
        </Alert>
      )}

      <AsyncState loading={loading} error={error}>
        {sorted.length === 0 ? (
          <Typography color="text.secondary">No meetings yet.</Typography>
        ) : (
          <>
            <Tabs value={effectiveYear} onChange={(_, year) => setSelectedYear(year)} variant="scrollable" scrollButtons="auto" allowScrollButtonsMobile sx={{ mb: 3 }}>
              {years.map((year) => (
                <Tab key={year} value={year} label={year} />
              ))}
            </Tabs>

            <Box ref={gridRef}>
              {months.map(({ month, meetings: monthMeetings }) => {
                const cards = monthMeetings.flatMap((meeting) => cardsFor(meeting, club, seasonNumbers, mediaFilters))
                if (cards.length === 0) return null
                const columns = balancedColumns(cards.length, gridWidth)
                return (
                  <Box key={month} sx={{ mb: 4 }}>
                    <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                      <Typography variant="h6" gutterBottom sx={{ mb: 0 }}>
                        {MONTH_FORMATTER.format(new Date(`${month}-01T00:00:00`))}
                      </Typography>
                      <IconButton
                        size="small"
                        onClick={() => handleShare(month, cards)}
                        disabled={sharingMonth === month}
                        title="Share this month as an image"
                      >
                        {sharingMonth === month ? <CircularProgress size={16} /> : <IosShareIcon fontSize="small" />}
                      </IconButton>
                    </Stack>
                    <Box
                      sx={
                        columns
                          ? { display: 'grid', gridTemplateColumns: `repeat(${columns}, ${POSTER_CARD_WIDTH}px)`, gap: 2 }
                          : { display: 'flex', flexWrap: 'wrap', gap: 2 }
                      }
                    >
                      {cards.map((card) => (
                        <PosterCard key={card.key} card={card} />
                      ))}
                    </Box>
                  </Box>
                )
              })}
            </Box>
          </>
        )}
      </AsyncState>
    </Box>
  )
}

function groupByMonth(meetings: MeetingWithPicks[]) {
  const order: string[] = []
  const byMonth = new Map<string, MeetingWithPicks[]>()
  for (const meeting of meetings) {
    const month = meeting.date.slice(0, 7)
    if (!byMonth.has(month)) {
      byMonth.set(month, [])
      order.push(month)
    }
    byMonth.get(month)!.push(meeting)
  }
  return order.map((month) => ({ month, meetings: byMonth.get(month)! }))
}

function cardsFor(
  meeting: MeetingWithPicks,
  club: ClubOutletContext['club'],
  seasonNumbers: Map<string, { number: number; seasonDigits: number; episodeDigits: number }> | null,
  mediaFilters: MediaTypeFilters,
): PosterCardInfo[] {
  const movieCards = mediaFilters.showMovies
    ? meeting.movies.map((pick: MeetingMoviePick) => ({
        key: pick.movie.id,
        meetingId: meeting.id,
        date: meeting.date,
        title: resolveTitle(pick.movie, club),
        posterUrl: pick.movie.posterUrl,
        isEpisode: false,
      }))
    : []

  const episodeCards = mediaFilters.showEpisodes
    ? meeting.episodes.map((pick: MeetingEpisodePick) => {
        const seasonCode = seasonNumbers?.get(pick.episode.seasonId)
        const code = episodeCode(seasonCode?.number, pick.episode.number, seasonCode?.seasonDigits, seasonCode?.episodeDigits)
        const seriesTitle = pick.series ? resolveTitle(pick.series, club) : null
        const codeAndName = `${code}${pick.episode.title ? ` - ${pick.episode.title}` : ''}`
        return {
          key: pick.episode.id,
          meetingId: meeting.id,
          date: meeting.date,
          title: seriesTitle ? `${seriesTitle} ${codeAndName}` : codeAndName,
          posterUrl: pick.series?.posterUrl ?? null,
          isEpisode: true,
        }
      })
    : []

  return [...movieCards, ...episodeCards]
}

function PosterCard({ card }: { card: PosterCardInfo }) {
  return (
    <Box
      component={RouterLink}
      to={`/meetings/${card.meetingId}`}
      sx={{
        width: 120,
        textDecoration: 'none',
        color: 'inherit',
        display: 'block',
        '&:hover': { opacity: 0.85 },
      }}
    >
      {card.posterUrl ? (
        <Box component="img" src={card.posterUrl} alt="" sx={{ width: '100%', aspectRatio: '2 / 3', objectFit: 'cover', borderRadius: 1 }} />
      ) : (
        <Box
          sx={{
            width: '100%',
            aspectRatio: '2 / 3',
            borderRadius: 1,
            bgcolor: 'action.hover',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'text.disabled',
          }}
        >
          {card.isEpisode ? <LiveTvIcon /> : <MovieIcon />}
        </Box>
      )}
      <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 0.5 }}>
        {card.date}
      </Typography>
      <Typography variant="body2" sx={{ lineHeight: 1.2 }}>
        {card.title}
      </Typography>
    </Box>
  )
}
