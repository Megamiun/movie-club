import { Alert, Box, Button, Paper, TextField, Typography } from '@mui/material'
import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/client'

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [inviteToken, setInviteToken] = useState(searchParams.get('token') ?? '')
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await register(inviteToken, name, password)
      navigate('/clubs', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Box display="flex" justifyContent="center" mt={8}>
      <Paper sx={{ p: 4, width: 360 }} component="form" onSubmit={handleSubmit}>
        <Typography variant="h5" gutterBottom>
          Register
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <TextField
          label="Invite token"
          fullWidth
          margin="normal"
          value={inviteToken}
          onChange={(e) => setInviteToken(e.target.value)}
          required
        />
        <TextField label="Name" fullWidth margin="normal" value={name} onChange={(e) => setName(e.target.value)} required />
        <TextField
          label="Password"
          type="password"
          fullWidth
          margin="normal"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        <Button type="submit" variant="contained" fullWidth sx={{ mt: 2 }} disabled={submitting}>
          Register
        </Button>
      </Paper>
    </Box>
  )
}
