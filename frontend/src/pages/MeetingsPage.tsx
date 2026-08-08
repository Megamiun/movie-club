import AddIcon from '@mui/icons-material/Add'
import {
  Alert,
  Box,
  Button,
  List,
  ListItemButton,
  ListItemText,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useState, type FormEvent } from 'react'
import { Link as RouterLink, useOutletContext } from 'react-router-dom'
import { meetingsApi } from '../api/meetings'
import { ApiError } from '../api/client'
import { AsyncState } from '../components/AsyncState'
import { useAsync } from '../hooks/useAsync'
import type { ClubOutletContext } from '../layout/ClubOutletContext'

export function MeetingsPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const { data: meetings, loading, error, reload } = useAsync(() => meetingsApi.list(club.id), [club.id])
  const [date, setDate] = useState('')
  const [assignedMemberId, setAssignedMemberId] = useState('')
  const [submitError, setSubmitError] = useState<string | null>(null)

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitError(null)
    try {
      await meetingsApi.create(club.id, date, assignedMemberId || undefined)
      setDate('')
      setAssignedMemberId('')
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
        <List>
          {sorted.length === 0 && <Typography color="text.secondary">No meetings yet.</Typography>}
          {sorted.map((meeting) => (
            <ListItemButton key={meeting.id} component={RouterLink} to={`/meetings/${meeting.id}`} divider>
              <ListItemText
                primary={meeting.date}
                secondary={meeting.assignedMemberId ? `Assigned: ${meeting.assignedMemberId}` : 'Shared / merged'}
              />
            </ListItemButton>
          ))}
        </List>
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
          <TextField
            label="Assigned member ID (optional)"
            size="small"
            value={assignedMemberId}
            onChange={(e) => setAssignedMemberId(e.target.value)}
          />
          <Button type="submit" variant="contained" startIcon={<AddIcon />}>
            Create
          </Button>
        </Stack>
      </Box>
    </Box>
  )
}
