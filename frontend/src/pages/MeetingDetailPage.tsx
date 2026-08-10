import DeleteIcon from '@mui/icons-material/Delete'
import { Alert, Box, Button, Divider, Stack, TextField, Typography } from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import { meetingsApi } from '../api/meetings'
import { clubsApi } from '../api/clubs'
import { ApiError } from '../api/client'
import { AsyncState } from '../components/AsyncState'
import { useAsync } from '../hooks/useAsync'
import { useSmartPolling } from '../hooks/useSmartPolling'
import { memberName } from '../utils/members'
import { MovieSection } from './meeting/MovieSection'
import { EpisodeSection } from './meeting/EpisodeSection'

export function MeetingDetailPage() {
  const { meetingId } = useParams<{ meetingId: string }>()
  const navigate = useNavigate()
  const { data: meeting, loading, error, reload, silentReload } = useAsync(() => meetingsApi.get(meetingId!), [meetingId])
  const { data: club } = useAsync(() => (meeting ? clubsApi.get(meeting.clubId) : Promise.resolve(null)), [
    meeting?.clubId,
  ])
  const { data: scales } = useAsync(() => (meeting ? clubsApi.getRatingScales(meeting.clubId) : Promise.resolve([])), [
    meeting?.clubId,
  ])

  useSmartPolling(silentReload, 15000)

  const languagePrefs = {
    preferredLanguages: club?.preferredLanguages ?? [],
    ignoredLanguages: club?.ignoredLanguages ?? [],
  }

  const [newDate, setNewDate] = useState('')
  const [otherMeetingId, setOtherMeetingId] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

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
            <Button component={RouterLink} to={`/clubs/${meeting.clubId}`} sx={{ mb: 2 }}>
              &larr; Back to meetings
            </Button>
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
              <TextField
                label="Other meeting ID"
                size="small"
                value={otherMeetingId}
                onChange={(e) => setOtherMeetingId(e.target.value)}
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
            />
            <Divider sx={{ my: 3 }} />
            <EpisodeSection
              meetingId={meeting.id}
              clubId={meeting.clubId}
              scales={scales ?? []}
              languagePrefs={languagePrefs}
            />
          </>
        )}
      </AsyncState>
    </Box>
  )
}
