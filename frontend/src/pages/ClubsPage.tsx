import AddIcon from '@mui/icons-material/Add'
import {
  Alert,
  Box,
  Button,
  Card,
  CardActionArea,
  CardContent,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useState, type FormEvent } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { clubsApi } from '../api/clubs'
import { ApiError } from '../api/client'
import { AsyncState } from '../components/AsyncState'
import { useAsync } from '../hooks/useAsync'

export function ClubsPage() {
  const { data: clubs, loading, error, reload } = useAsync(() => clubsApi.list(), [])
  const [dialogOpen, setDialogOpen] = useState(false)
  const [name, setName] = useState('')
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setSubmitError(null)
    try {
      await clubsApi.create(name)
      setName('')
      setDialogOpen(false)
      reload()
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Something went wrong')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4">Clubs</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
          New club
        </Button>
      </Stack>

      <AsyncState loading={loading} error={error}>
        <Stack spacing={2}>
          {clubs?.length === 0 && <Typography color="text.secondary">No clubs yet.</Typography>}
          {clubs?.map((club) => (
            <Card key={club.id}>
              <CardActionArea component={RouterLink} to={`/clubs/${club.id}`}>
                <CardContent>
                  <Typography variant="h6">{club.name}</Typography>
                </CardContent>
              </CardActionArea>
            </Card>
          ))}
        </Stack>
      </AsyncState>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="xs">
        <Box component="form" onSubmit={handleCreate}>
          <DialogTitle>Create club</DialogTitle>
          <DialogContent>
            {submitError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {submitError}
              </Alert>
            )}
            <TextField
              autoFocus
              label="Club name"
              fullWidth
              margin="dense"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={submitting}>
              Create
            </Button>
          </DialogActions>
        </Box>
      </Dialog>
    </Box>
  )
}
