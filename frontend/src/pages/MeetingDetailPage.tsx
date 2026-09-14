import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import EventRepeatIcon from '@mui/icons-material/EventRepeat'
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  Menu,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import { meetingsApi } from '../api/meetings'
import { clubsApi } from '../api/clubs'
import { ApiError } from '../api/client'
import { AsyncState } from '../components/AsyncState'
import { useAsync } from '../hooks/useAsync'
import { useSmartPolling } from '../hooks/useSmartPolling'
import { useDateDisplay } from '../settings/DateDisplayContext'
import { formatMeetingDate } from '../utils/date'
import { orderMeetingsByProximity } from '../utils/meetings'
import { MovieSection } from './meeting/MovieSection'
import { EpisodeSection } from './meeting/EpisodeSection'

export function MeetingDetailPage() {
  const { meetingId } = useParams<{ meetingId: string }>()
  const navigate = useNavigate()
  const { dateStyle } = useDateDisplay()
  const { data: meeting, loading, error, reload, silentReload } = useAsync(() => meetingsApi.get(meetingId!), [meetingId])
  const { data: club, silentReload: silentReloadClub } = useAsync(
    () => (meeting ? clubsApi.get(meeting.clubId) : Promise.resolve(null)),
    [meeting?.clubId],
  )
  const { data: scales, silentReload: silentReloadScales } = useAsync(
    () => (meeting ? clubsApi.getRatingScales(meeting.clubId) : Promise.resolve([])),
    [meeting?.clubId],
  )
  const { data: clubMeetings, silentReload: silentReloadClubMeetings } = useAsync(
    () => (meeting ? meetingsApi.list(meeting.clubId) : Promise.resolve([])),
    [meeting?.clubId],
  )

  useSmartPolling(() => {
    silentReload()
    silentReloadClub()
    silentReloadScales()
    silentReloadClubMeetings()
  }, 7500)

  const languagePrefs = {
    preferredLanguages: club?.preferredLanguages ?? [],
    ignoredLanguages: club?.ignoredLanguages ?? [],
  }

  const [newDate, setNewDate] = useState('')
  const [otherMeetingId, setOtherMeetingId] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)
  const [addChoice, setAddChoice] = useState<'movie' | 'series' | null>(null)
  const [addMenuAnchor, setAddMenuAnchor] = useState<HTMLElement | null>(null)
  const [rescheduleOpen, setRescheduleOpen] = useState(false)
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)

  const otherMeetings = meeting && clubMeetings ? orderMeetingsByProximity(clubMeetings, meeting.date, meeting.id) : []

  const handlePostpone = async () => {
    if (!newDate || !meetingId) return
    setActionError(null)
    try {
      await meetingsApi.postpone(meetingId, newDate)
      setNewDate('')
      reload()
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleSwap = async () => {
    if (!otherMeetingId || !meetingId) return
    setActionError(null)
    try {
      await meetingsApi.swap(meetingId, otherMeetingId)
      setOtherMeetingId('')
      reload()
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleMerge = async () => {
    if (!otherMeetingId || !meetingId) return
    setActionError(null)
    try {
      await meetingsApi.merge(meetingId, otherMeetingId)
      setOtherMeetingId('')
      reload()
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleDelete = async () => {
    if (!meetingId || !meeting) return
    setActionError(null)
    try {
      await meetingsApi.remove(meetingId)
      navigate(`/clubs/${meeting.clubId}`)
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong')
      setDeleteConfirmOpen(false)
    }
  }

  return (
    <Box>
      <AsyncState loading={loading} error={error}>
        {meeting && (
          <>
            <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
              <Button component={RouterLink} to={`/clubs/${meeting.clubId}`}>
                &larr; Back to meetings
              </Button>
              <Button component={RouterLink} to={`/clubs/${meeting.clubId}/watchlist`}>
                &larr; Back to watchlist
              </Button>
            </Stack>

            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
              <Typography variant="h4">Meeting — {formatMeetingDate(meeting.date, dateStyle)}</Typography>
              <IconButton onClick={(e) => setAddMenuAnchor(e.currentTarget)} title="Add movie or series">
                <AddIcon />
              </IconButton>
              <Menu anchorEl={addMenuAnchor} open={Boolean(addMenuAnchor)} onClose={() => setAddMenuAnchor(null)}>
                <MenuItem
                  onClick={() => {
                    setAddChoice('movie')
                    setAddMenuAnchor(null)
                  }}
                >
                  Movie
                </MenuItem>
                <MenuItem
                  onClick={() => {
                    setAddChoice('series')
                    setAddMenuAnchor(null)
                  }}
                >
                  Series
                </MenuItem>
              </Menu>
              <IconButton onClick={() => setRescheduleOpen(true)} title="Postpone, swap, or merge this meeting">
                <EventRepeatIcon />
              </IconButton>
              <IconButton onClick={() => setDeleteConfirmOpen(true)} title="Delete this meeting">
                <DeleteIcon />
              </IconButton>
            </Stack>

            {actionError && (
              <Alert severity="error" sx={{ my: 2 }}>
                {actionError}
              </Alert>
            )}

            <Dialog open={rescheduleOpen} onClose={() => setRescheduleOpen(false)} fullWidth maxWidth="xs">
              <DialogTitle>Postpone, swap, or merge</DialogTitle>
              <DialogContent>
                <Stack spacing={2} sx={{ mt: 1 }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <TextField
                      label="New date"
                      type="date"
                      size="small"
                      value={newDate}
                      onChange={(e) => setNewDate(e.target.value)}
                      slotProps={{ inputLabel: { shrink: true } }}
                      fullWidth
                    />
                    <Button size="small" variant="outlined" onClick={handlePostpone} disabled={!newDate}>
                      Postpone
                    </Button>
                  </Stack>
                  <Divider />
                  <Autocomplete
                    size="small"
                    options={otherMeetings}
                    getOptionLabel={(m) => formatMeetingDate(m.date, dateStyle)}
                    isOptionEqualToValue={(a, b) => a.id === b.id}
                    value={otherMeetings.find((m) => m.id === otherMeetingId) ?? null}
                    onChange={(_, option) => setOtherMeetingId(option?.id ?? '')}
                    renderInput={(params) => <TextField {...params} label="Other meeting" />}
                  />
                  <Stack direction="row" spacing={1}>
                    <Button size="small" variant="outlined" onClick={handleSwap} disabled={!otherMeetingId} fullWidth>
                      Swap assignment
                    </Button>
                    <Button size="small" variant="outlined" onClick={handleMerge} disabled={!otherMeetingId} fullWidth>
                      Merge from
                    </Button>
                  </Stack>
                </Stack>
              </DialogContent>
              <DialogActions>
                <Button onClick={() => setRescheduleOpen(false)}>Close</Button>
              </DialogActions>
            </Dialog>

            <Dialog open={deleteConfirmOpen} onClose={() => setDeleteConfirmOpen(false)} maxWidth="xs">
              <DialogTitle>Delete this meeting?</DialogTitle>
              <DialogContent>
                <Typography variant="body2" color="text.secondary">
                  This removes the meeting itself. Movies and episodes picked here are not deleted separately by
                  this action -- they go with it.
                </Typography>
              </DialogContent>
              <DialogActions>
                <Button onClick={() => setDeleteConfirmOpen(false)}>Cancel</Button>
                <Button color="error" variant="contained" startIcon={<DeleteIcon />} onClick={handleDelete}>
                  Delete meeting
                </Button>
              </DialogActions>
            </Dialog>

            <MovieSection
              meetingId={meeting.id}
              clubId={meeting.clubId}
              scales={scales ?? []}
              members={club?.members ?? []}
              languagePrefs={languagePrefs}
              showAddForm={addChoice === 'movie'}
              onCloseAddForm={() => setAddChoice(null)}
            />
            <EpisodeSection
              meetingId={meeting.id}
              clubId={meeting.clubId}
              scales={scales ?? []}
              languagePrefs={languagePrefs}
              showAddForm={addChoice === 'series'}
              onCloseAddForm={() => setAddChoice(null)}
            />
          </>
        )}
      </AsyncState>
    </Box>
  )
}
