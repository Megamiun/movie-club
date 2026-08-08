import AddIcon from '@mui/icons-material/Add'
import { Alert, Box, Button, List, ListItemButton, ListItemText, Stack, TextField, Typography } from '@mui/material'
import { useState, type FormEvent } from 'react'
import { Link as RouterLink, useOutletContext } from 'react-router-dom'
import { seriesApi } from '../api/series'
import { ApiError } from '../api/client'
import { AsyncState } from '../components/AsyncState'
import { useAsync } from '../hooks/useAsync'
import type { ClubOutletContext } from '../layout/ClubOutletContext'

export function SeriesListPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const { data: series, loading, error, reload } = useAsync(() => seriesApi.list(club.id), [club.id])
  const [imdbUrlOrId, setImdbUrlOrId] = useState('')
  const [submitError, setSubmitError] = useState<string | null>(null)

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitError(null)
    try {
      await seriesApi.add(club.id, imdbUrlOrId)
      setImdbUrlOrId('')
      reload()
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Series
      </Typography>

      <AsyncState loading={loading} error={error}>
        <List>
          {series?.length === 0 && <Typography color="text.secondary">No series followed yet.</Typography>}
          {series?.map((s) => (
            <ListItemButton key={s.id} component={RouterLink} to={`/series/${s.id}`} divider>
              <ListItemText
                primary={s.customTitle ?? s.originalTitle}
                secondary={[s.year, s.creator].filter(Boolean).join(' · ')}
              />
            </ListItemButton>
          ))}
        </List>
      </AsyncState>

      <Box component="form" onSubmit={handleAdd} sx={{ mt: 3 }}>
        {submitError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {submitError}
          </Alert>
        )}
        <Stack direction="row" spacing={1}>
          <TextField
            label="IMDB URL or tt id"
            size="small"
            value={imdbUrlOrId}
            onChange={(e) => setImdbUrlOrId(e.target.value)}
            required
            fullWidth
          />
          <Button type="submit" variant="contained" startIcon={<AddIcon />}>
            Add series
          </Button>
        </Stack>
      </Box>
    </Box>
  )
}
