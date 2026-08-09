import AddIcon from '@mui/icons-material/Add'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import DeleteIcon from '@mui/icons-material/Delete'
import DragIndicatorIcon from '@mui/icons-material/DragIndicator'
import EventIcon from '@mui/icons-material/Event'
import { Alert, Box, Button, Chip, IconButton, MenuItem, Paper, Select, Stack, TextField, Typography } from '@mui/material'
import {
  DndContext,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import { SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable'
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
import { TmdbSearchAutocomplete } from '../components/TmdbSearchAutocomplete'
import { useAuth } from '../auth/AuthContext'
import { useAsync } from '../hooks/useAsync'
import type { ClubOutletContext } from '../layout/ClubOutletContext'
import { memberName } from '../utils/members'
import { ratingLabel } from '../utils/rating'

export function WatchlistPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const { member } = useAuth()
  const { data: entries, loading, error, reload } = useAsync(() => watchlistApi.list(club.id), [club.id])
  const { data: meetings } = useAsync(() => meetingsApi.list(club.id), [club.id])
  const sortedMeetings = [...(meetings ?? [])].sort((a, b) => a.date.localeCompare(b.date))

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Watchlist
      </Typography>

      <AsyncState loading={loading} error={error}>
        <Stack spacing={4}>
          <WatchlistSection
            type="MOVIE"
            title="Movies"
            search={moviesApi.search}
            entries={entries?.filter((entry) => entry.type === 'MOVIE') ?? []}
            members={club.members}
            clubId={club.id}
            meetings={sortedMeetings}
            myMemberId={member?.id ?? null}
            onChange={reload}
          />
          <WatchlistSection
            type="SERIES"
            title="Series"
            search={seriesApi.search}
            entries={entries?.filter((entry) => entry.type === 'SERIES') ?? []}
            members={club.members}
            clubId={club.id}
            meetings={[]}
            myMemberId={member?.id ?? null}
            onChange={reload}
          />
        </Stack>
      </AsyncState>
    </Box>
  )
}

function WatchlistSection({
  type,
  title,
  search,
  entries,
  members,
  clubId,
  meetings,
  myMemberId,
  onChange,
}: {
  type: 'MOVIE' | 'SERIES'
  title: string
  search: (query: string) => Promise<TmdbSearchResult[]>
  entries: WatchlistEntry[]
  members: ClubMember[]
  clubId: string
  meetings: Meeting[]
  myMemberId: string | null
  onChange: () => void
}) {
  const [selectedResult, setSelectedResult] = useState<TmdbSearchResult | null>(null)
  const [notes, setNotes] = useState('')
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [dragError, setDragError] = useState<string | null>(null)
  const sorted = [...entries].sort((a, b) => a.position - b.position)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedResult) return
    setSubmitError(null)
    try {
      await watchlistApi.add(clubId, type, selectedResult.tmdbId, notes || undefined)
      setSelectedResult(null)
      setNotes('')
      onChange()
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  /** The backend only supports swapping with an *adjacent* sibling (see `WatchlistService.moveEntry`) -- dropping
   * further away just replays that same swap one step at a time until the dragged entry reaches where it was
   * dropped, reusing the up/down buttons' own primitive instead of adding a "set exact position" endpoint. */
  const handleDragEnd = async (event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const oldIndex = sorted.findIndex((entry) => entry.id === active.id)
    const newIndex = sorted.findIndex((entry) => entry.id === over.id)
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
      <Typography variant="h6" gutterBottom>
        {title}
      </Typography>

      {sorted.length === 0 && (
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          Nothing here yet.
        </Typography>
      )}

      {dragError && (
        <Alert severity="error" sx={{ mb: 1 }} onClose={() => setDragError(null)}>
          {dragError}
        </Alert>
      )}

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={sorted.map((entry) => entry.id)} strategy={verticalListSortingStrategy}>
          <Stack spacing={1} sx={{ mb: 2 }}>
            {sorted.map((entry, index) => (
              <WatchlistEntryCard
                key={entry.id}
                entry={entry}
                members={members}
                canMoveUp={index > 0}
                canMoveDown={index < sorted.length - 1}
                meetings={meetings}
                isOwner={entry.memberId === myMemberId}
                onChange={onChange}
              />
            ))}
          </Stack>
        </SortableContext>
      </DndContext>

      {submitError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {submitError}
        </Alert>
      )}
      <Box component="form" onSubmit={handleAdd}>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
          <TmdbSearchAutocomplete
            search={search}
            value={selectedResult}
            onChange={setSelectedResult}
            label={`Search ${title.toLowerCase()}`}
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

function WatchlistEntryCard({
  entry,
  members,
  canMoveUp,
  canMoveDown,
  meetings,
  isOwner,
  onChange,
}: {
  entry: WatchlistEntry
  members: ClubMember[]
  canMoveUp: boolean
  canMoveDown: boolean
  meetings: Meeting[]
  isOwner: boolean
  onChange: () => void
}) {
  const [notes, setNotes] = useState(entry.notes ?? '')
  const [targetMeetingId, setTargetMeetingId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: entry.id })

  const handleBlurSave = async () => {
    if (notes === (entry.notes ?? '')) return
    setError(null)
    try {
      await watchlistApi.update(entry.id, notes || undefined)
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleMove = async (direction: 'UP' | 'DOWN') => {
    setError(null)
    try {
      await watchlistApi.move(entry.id, direction)
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

  const handleMoveToMeeting = async () => {
    if (!targetMeetingId) return
    setError(null)
    try {
      await moviesApi.add(targetMeetingId, entry.imdbId)
      await watchlistApi.remove(entry.id)
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const rating = ratingLabel(entry)
  const canMoveToMeeting = isOwner && entry.type === 'MOVIE' && meetings.length > 0

  return (
    <Paper
      ref={setNodeRef}
      variant="outlined"
      sx={{
        p: 1.5,
        display: 'flex',
        gap: 1.5,
        alignItems: 'center',
        opacity: isDragging ? 0.5 : 1,
        transform: CSS.Transform.toString(transform),
        transition,
      }}
    >
      <Box
        {...attributes}
        {...listeners}
        sx={{ display: 'flex', alignItems: 'center', cursor: 'grab', color: 'text.disabled', flexShrink: 0 }}
        title="Drag to reorder"
      >
        <DragIndicatorIcon fontSize="small" />
      </Box>
      {entry.posterUrl && (
        <Box component="img" src={entry.posterUrl} alt="" sx={{ width: 46, borderRadius: 0.5, flexShrink: 0 }} />
      )}
      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography sx={{ fontWeight: 500 }}>{entry.title}</Typography>
          {entry.year && <Chip size="small" label={entry.year} />}
          {rating && <Chip size="small" label={rating} />}
          <ImdbLink imdbId={entry.imdbId} />
        </Stack>
        <TextField
          variant="standard"
          placeholder="Notes"
          fullWidth
          size="small"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          onBlur={handleBlurSave}
        />
        <Typography variant="caption" color="text.secondary">
          Added by {memberName(members, entry.memberId)}
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mt: 1 }}>
            {error}
          </Alert>
        )}
      </Box>
      {canMoveToMeeting && (
        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', flexShrink: 0 }}>
          <Select
            size="small"
            displayEmpty
            value={targetMeetingId}
            onChange={(e) => setTargetMeetingId(e.target.value)}
            sx={{ minWidth: 120 }}
          >
            <MenuItem value="">
              <em>Move to meeting…</em>
            </MenuItem>
            {meetings.map((meeting) => (
              <MenuItem key={meeting.id} value={meeting.id}>
                {meeting.date}
              </MenuItem>
            ))}
          </Select>
          <IconButton size="small" onClick={handleMoveToMeeting} disabled={!targetMeetingId} title="Move to meeting">
            <EventIcon fontSize="small" />
          </IconButton>
        </Stack>
      )}
      <Stack sx={{ flexShrink: 0 }}>
        <IconButton size="small" onClick={() => handleMove('UP')} disabled={!canMoveUp} title="Move up">
          <ArrowUpwardIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" onClick={() => handleMove('DOWN')} disabled={!canMoveDown} title="Move down">
          <ArrowDownwardIcon fontSize="small" />
        </IconButton>
      </Stack>
      <IconButton size="small" onClick={handleDelete} title="Remove" sx={{ flexShrink: 0 }}>
        <DeleteIcon fontSize="small" />
      </IconButton>
    </Paper>
  )
}
