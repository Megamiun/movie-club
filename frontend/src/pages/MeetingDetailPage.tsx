import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import { Alert, Autocomplete, Box, Button, Divider, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import { meetingsApi } from '../api/meetings'
import { clubsApi } from '../api/clubs'
import { ApiError } from '../api/client'
import { AsyncState } from '../components/AsyncState'
import { useAsync } from '../hooks/useAsync'
import { useSmartPolling } from '../hooks/useSmartPolling'
import { memberName } from '../utils/members'
import { orderMeetingsByProximity } from '../utils/meetings'
import { MovieSection } from './meeting/MovieSection'
import { EpisodeSection } from './meeting/EpisodeSection'

export function MeetingDetailPage() {
  const { meetingId } = useParams<{ meetingId: string }>()
  const navigate = useNavigate()
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
            <Typography variant="h4" gutterBottom>
              Meeting — {meeting.date}
            </Typography>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              {meeting.assignedMemberId
                ? `Assigned to ${memberName(club?.members ?? [], meeting.assignedMemberId)}`
                : 'Shared / merged meeting'}
            </Typography>

            {actionError && (
              <Alert severity="error" sx={{ my: 2 }}>
                {actionError}
              </Alert>
            )}

            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', my: 2 }}>
              <AddIcon fontSize="small" color="action" />
              <Typography variant="body2" color="text.secondary">
                Add:
              </Typography>
              <ToggleButtonGroup
                size="small"
                exclusive
                value={addChoice}
                onChange={(_, value) => setAddChoice(value)}
              >
                <ToggleButton value="movie">Movie</ToggleButton>
                <ToggleButton value="series">Series</ToggleButton>
              </ToggleButtonGroup>
            </Stack>

            <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', alignItems: 'center', my: 2 }}>
              <TextField
                label="New date"
                type="date"
                size="small"
                value={newDate}
                onChange={(e) => setNewDate(e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <Button size="small" variant="outlined" onClick={handlePostpone}>
                Postpone
              </Button>
              <Autocomplete
                size="small"
                options={otherMeetings}
                getOptionLabel={(m) => m.date}
                isOptionEqualToValue={(a, b) => a.id === b.id}
                value={otherMeetings.find((m) => m.id === otherMeetingId) ?? null}
                onChange={(_, option) => setOtherMeetingId(option?.id ?? '')}
                renderInput={(params) => <TextField {...params} label="Other meeting" />}
                sx={{ minWidth: 160 }}
              />
              <Button size="small" variant="outlined" onClick={handleSwap}>
                Swap assignment
              </Button>
              <Button size="small" variant="outlined" onClick={handleMerge}>
                Merge from
              </Button>
              <Button size="small" color="error" variant="outlined" startIcon={<DeleteIcon />} onClick={handleDelete}>
                Delete meeting
              </Button>
            </Stack>

            <Divider sx={{ my: 3 }} />
            <MovieSection
              meetingId={meeting.id}
              clubId={meeting.clubId}
              scales={scales ?? []}
              members={club?.members ?? []}
              languagePrefs={languagePrefs}
              showAddForm={addChoice === 'movie'}
            />
            <Divider sx={{ my: 3 }} />
            <EpisodeSection
              meetingId={meeting.id}
              clubId={meeting.clubId}
              scales={scales ?? []}
              languagePrefs={languagePrefs}
              showAddForm={addChoice === 'series'}
            />
          </>
        )}
      </AsyncState>
    </Box>
  )
}
