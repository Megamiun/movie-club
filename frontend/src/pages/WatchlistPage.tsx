import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import DragIndicatorIcon from '@mui/icons-material/DragIndicator'
import EventIcon from '@mui/icons-material/Event'
import LiveTvIcon from '@mui/icons-material/LiveTv'
import MovieIcon from '@mui/icons-material/Movie'
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  IconButton,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material'
import {
  DndContext,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import { SortableContext, rectSortingStrategy, useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { useState, type FormEvent } from 'react'
import { useOutletContext } from 'react-router-dom'
import { meetingsApi } from '../api/meetings'
import { moviesApi } from '../api/movies'
import { seriesApi } from '../api/series'
import { watchlistApi } from '../api/watchlist'
import { ApiError } from '../api/client'
import type { ClubMember, Meeting, TmdbSearchResult, WatchlistEntry } from '../api/types'
import { AsyncState } from '../components/AsyncState'
import { ImdbLink } from '../components/ImdbLink'
import { MemberBadge } from '../components/MemberBadge'
import { TmdbSearchAutocomplete } from '../components/TmdbSearchAutocomplete'
import { useAuth } from '../auth/AuthContext'
import { useAsync } from '../hooks/useAsync'
import { useSmartPolling } from '../hooks/useSmartPolling'
import type { ClubOutletContext } from '../layout/ClubOutletContext'
import { orderMeetingsByProximity } from '../utils/meetings'
import { ratingLabel } from '../utils/rating'
import { resolveTitle, type LanguagePreferences } from '../utils/title'

/** One mixed, member-ordered list (movies and series together, see `WatchlistService.moveEntry`/`create` on the
 * backend) rather than the old two-board-per-type layout -- each member gets one full-width section, viewer's own
 * first, then everyone else in the club's rotation order, so on a phone you see your own whole list before
 * anyone else's instead of a cramped side-scrolling column per member. */
export function WatchlistPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const { member } = useAuth()
  const { data: entries, loading, error, silentReload } = useAsync(() => watchlistApi.list(club.id), [club.id])
  const { data: meetings } = useAsync(() => meetingsApi.list(club.id), [club.id])

  useSmartPolling(silentReload, 15000)
  const sortedMeetings = [...(meetings ?? [])].sort((a, b) => a.date.localeCompare(b.date))
  const languagePrefs: LanguagePreferences = { preferredLanguages: club.preferredLanguages, ignoredLanguages: club.ignoredLanguages }

  // Viewer's own section always first, everyone else afterwards in the club's usual rotation order.
  const orderedMembers = [...club.members].sort((a, b) => {
    if (a.memberId === member?.id) return -1
    if (b.memberId === member?.id) return 1
    return a.rotationOrder - b.rotationOrder
  })

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Watchlist
      </Typography>

      <AsyncState loading={loading} error={error}>
        <Stack spacing={4}>
          {orderedMembers.map((sectionMember) => (
            <WatchlistMemberSection
              key={sectionMember.memberId}
              member={sectionMember}
              entries={(entries ?? [])
                .filter((entry) => entry.memberId === sectionMember.memberId)
                .sort((a, b) => a.position - b.position)}
              isOwnSection={sectionMember.memberId === member?.id}
              clubId={club.id}
              meetings={sortedMeetings}
              languagePrefs={languagePrefs}
              onChange={silentReload}
            />
          ))}
        </Stack>
      </AsyncState>
    </Box>
  )
}

const CARD_WIDTH = 150

function WatchlistMemberSection({
  member,
  entries,
  isOwnSection,
  clubId,
  meetings,
  languagePrefs,
  onChange,
}: {
  member: ClubMember
  entries: WatchlistEntry[]
  isOwnSection: boolean
  clubId: string
  meetings: Meeting[]
  languagePrefs: LanguagePreferences
  onChange: () => void
}) {
  const [dragError, setDragError] = useState<string | null>(null)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  /** The backend only supports swapping with an *adjacent* sibling within this member's own list (see
   * `WatchlistService.moveEntry`) -- dropping further away just replays that same swap one step at a time until
   * the dragged entry reaches where it was dropped, rather than adding a "set exact position" endpoint. Each
   * section gets its own `DndContext`, so a card can never even be dropped into a different member's section in
   * the first place -- entries are personal, ownership isn't reassignable. `rectSortingStrategy` (not
   * `verticalListSortingStrategy`) since cards now wrap into a grid, not a single column. */
  const handleDragEnd = async (event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const oldIndex = entries.findIndex((entry) => entry.id === active.id)
    const newIndex = entries.findIndex((entry) => entry.id === over.id)
    if (oldIndex === -1 || newIndex === -1) return

    const direction = newIndex > oldIndex ? 'DOWN' : 'UP'
    setDragError(null)
    try {
      for (let step = 0; step < Math.abs(newIndex - oldIndex); step++) {
        await watchlistApi.move(active.id as string, direction)
      }
      onChange()
    } catch (err) {
      setDragError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <Box>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
        <MemberBadge member={member} size={28} />
        <Typography variant="h6">{member.name}</Typography>
      </Stack>

      {entries.length === 0 && (
        <Typography variant="body2" color="text.secondary">
          Nothing here yet.
        </Typography>
      )}

      {dragError && (
        <Alert severity="error" sx={{ mb: 1 }} onClose={() => setDragError(null)}>
          {dragError}
        </Alert>
      )}

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={entries.map((entry) => entry.id)} strategy={rectSortingStrategy}>
          <Box sx={{ display: 'grid', gridTemplateColumns: `repeat(auto-fill, minmax(${CARD_WIDTH}px, 1fr))`, gap: 1.5 }}>
            {entries.map((entry) => (
              <WatchlistCard
                key={entry.id}
                entry={entry}
                meetings={meetings}
                isOwner={isOwnSection}
                languagePrefs={languagePrefs}
                onChange={onChange}
              />
            ))}
          </Box>
        </SortableContext>
      </DndContext>

      {isOwnSection && <AddToWatchlistForm clubId={clubId} onChange={onChange} />}
    </Box>
  )
}

function AddToWatchlistForm({ clubId, onChange }: { clubId: string; onChange: () => void }) {
  const [type, setType] = useState<'MOVIE' | 'SERIES'>('MOVIE')
  const [selectedResult, setSelectedResult] = useState<TmdbSearchResult | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedResult) return
    setSubmitError(null)
    try {
      await watchlistApi.add(clubId, type, selectedResult.tmdbId)
      setSelectedResult(null)
      onChange()
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <Box component="form" onSubmit={handleAdd} sx={{ mt: 2 }}>
      {submitError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {submitError}
        </Alert>
      )}
      <Stack spacing={1}>
        <ToggleButtonGroup
          size="small"
          exclusive
          value={type}
          onChange={(_, value) => {
            if (value) {
              setType(value)
              setSelectedResult(null)
            }
          }}
        >
          <ToggleButton value="MOVIE">Movie</ToggleButton>
          <ToggleButton value="SERIES">Series</ToggleButton>
        </ToggleButtonGroup>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
          <TmdbSearchAutocomplete
            search={type === 'MOVIE' ? moviesApi.search : seriesApi.search}
            value={selectedResult}
            onChange={setSelectedResult}
            label={`Search ${type === 'MOVIE' ? 'movies' : 'series'}`}
          />
          <Button type="submit" variant="contained" startIcon={<AddIcon />}>
            Add to my list
          </Button>
        </Stack>
      </Stack>
    </Box>
  )
}

function WatchlistCard({
  entry,
  meetings,
  isOwner,
  languagePrefs,
  onChange,
}: {
  entry: WatchlistEntry
  meetings: Meeting[]
  isOwner: boolean
  languagePrefs: LanguagePreferences
  onChange: () => void
}) {
  const [targetMeetingId, setTargetMeetingId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: entry.id })

  const handleDelete = async () => {
    setError(null)
    try {
      await watchlistApi.remove(entry.id)
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleMoveToMeeting = async () => {
    if (!targetMeetingId) return
    setError(null)
    try {
      await watchlistApi.moveToMeeting(entry.id, targetMeetingId)
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const rating = ratingLabel(entry)
  // Not owner-restricted, unlike editing/deleting an entry -- any club member may schedule a movie sitting in
  // someone else's Watchlist onto a meeting, the same way any member can already add a movie to a meeting from
  // scratch (see WatchlistService.moveEntryToMeeting).
  const canMoveToMeeting = entry.type === 'MOVIE' && meetings.length > 0
  const orderedMeetings = orderMeetingsByProximity(meetings, new Date().toISOString().slice(0, 10))
  // A watchlist entry has no Movie/Series pick of its own, so no customTitle/displayTitlePreference/
  // displayLanguageCode to read -- its title always resolves as if ORIGINAL (see CLAUDE.md's WatchlistEntry
  // section and the backend's WatchlistEntryRow doc comment for why).
  const title = resolveTitle(
    {
      originalTitle: entry.title,
      originalLanguage: entry.originalLanguage,
      translations: entry.translations,
      customTitle: null,
      displayTitlePreference: 'ORIGINAL',
      displayLanguageCode: null,
    },
    languagePrefs,
  )

  return (
    <Box
      ref={setNodeRef}
      sx={{
        opacity: isDragging ? 0.5 : 1,
        transform: CSS.Transform.toString(transform),
        transition,
      }}
    >
      <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', mb: 0.25 }}>
        <Box
          {...attributes}
          {...listeners}
          sx={{ display: 'flex', alignItems: 'center', cursor: 'grab', color: 'text.disabled', touchAction: 'none' }}
          title="Drag to reorder"
        >
          <DragIndicatorIcon fontSize="small" />
        </Box>
        {isOwner && (
          <IconButton size="small" onClick={handleDelete} title="Remove" sx={{ ml: 'auto', p: 0.25 }}>
            <DeleteIcon fontSize="small" />
          </IconButton>
        )}
      </Stack>

      {entry.posterUrl ? (
        <Box component="img" src={entry.posterUrl} alt="" sx={{ width: '100%', aspectRatio: '2 / 3', objectFit: 'cover', borderRadius: 1 }} />
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
          {entry.type === 'SERIES' ? <LiveTvIcon /> : <MovieIcon />}
        </Box>
      )}

      <Typography variant="body2" sx={{ fontWeight: 500, mt: 0.5, lineHeight: 1.2 }}>
        {title}
      </Typography>
      <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', flexWrap: 'wrap', mt: 0.25 }}>
        {entry.year && <Chip size="small" label={entry.year} />}
        {rating && <Chip size="small" label={rating} />}
        <ImdbLink imdbId={entry.imdbId} />
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mt: 0.5 }}>
          {error}
        </Alert>
      )}

      {canMoveToMeeting && (
        <Stack spacing={0.5} sx={{ mt: 0.5 }}>
          <Autocomplete
            size="small"
            options={orderedMeetings}
            getOptionLabel={(m) => m.date}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            value={orderedMeetings.find((m) => m.id === targetMeetingId) ?? null}
            onChange={(_, option) => setTargetMeetingId(option?.id ?? '')}
            renderInput={(params) => <TextField {...params} label="Move to meeting" />}
          />
          <Button
            size="small"
            variant="outlined"
            startIcon={<EventIcon fontSize="small" />}
            onClick={handleMoveToMeeting}
            disabled={!targetMeetingId}
          >
            Move
          </Button>
        </Stack>
      )}
    </Box>
  )
}
