import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import { Alert, Box, Button, IconButton, Paper, TextField, Typography } from '@mui/material'
import { useState, type FormEvent } from 'react'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'

export function InvitePage() {
  const [email, setEmail] = useState('')
  const [inviteToken, setInviteToken] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setInviteToken(null)
    try {
      const response = await authApi.invite(email)
      setInviteToken(response.inviteToken)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    } finally {
      setSubmitting(false)
    }
  }

  const registerUrl = inviteToken
    ? `${window.location.origin}/register?token=${inviteToken}`
    : ''

  return (
    <Box sx={{ maxWidth: 480 }}>
      <Typography variant="h4" gutterBottom>
        Invite a member
      </Typography>
      <Paper sx={{ p: 3 }} component="form" onSubmit={handleSubmit}>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <TextField
          label="Email"
          type="email"
          fullWidth
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <Button type="submit" variant="contained" sx={{ mt: 2 }} disabled={submitting}>
          Send invite
        </Button>
      </Paper>
      {inviteToken && (
        <Paper sx={{ p: 3, mt: 2 }}>
          <Typography variant="body2" gutterBottom>
            Share this registration link:
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <TextField value={registerUrl} fullWidth size="small" slotProps={{ input: { readOnly: true } }} />
            <IconButton onClick={() => navigator.clipboard.writeText(registerUrl)} title="Copy link">
              <ContentCopyIcon />
            </IconButton>
          </Box>
        </Paper>
      )}
    </Box>
  )
}
