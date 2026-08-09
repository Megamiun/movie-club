import AddIcon from '@mui/icons-material/Add'
import {
  Alert,
  Box,
  Button,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material'
import { useState, type FormEvent } from 'react'
import { Link as RouterLink, useOutletContext } from 'react-router-dom'
import { seriesApi } from '../api/series'
import { ApiError } from '../api/client'
import type { TmdbSearchResult } from '../api/types'
import { AsyncState } from '../components/AsyncState'
import { ImdbLink } from '../components/ImdbLink'
import { TmdbSearchAutocomplete } from '../components/TmdbSearchAutocomplete'
import { useAsync } from '../hooks/useAsync'
import type { ClubOutletContext } from '../layout/ClubOutletContext'
import { resolveTitle } from '../utils/title'

export function SeriesListPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const { data: series, loading, error, reload } = useAsync(() => seriesApi.list(club.id), [club.id])
  const [addMode, setAddMode] = useState<'search' | 'imdb'>('search')
  const [selectedResult, setSelectedResult] = useState<TmdbSearchResult | null>(null)
  const [imdbUrlOrId, setImdbUrlOrId] = useState('')
  const [submitError, setSubmitError] = useState<string | null>(null)

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitError(null)
    try {
      if (addMode === 'search') {
        if (!selectedResult) return
        await seriesApi.addByTmdbId(club.id, selectedResult.tmdbId)
        setSelectedResult(null)
      } else {
        await seriesApi.add(club.id, imdbUrlOrId)
        setImdbUrlOrId('')
      }
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
            <ListItem key={s.id} divider disablePadding secondaryAction={<ImdbLink imdbId={s.imdbId} />}>
              <ListItemButton component={RouterLink} to={`/series/${s.id}`}>
                <ListItemText
                  primary={resolveTitle(s, club)}
                  secondary={[s.year, s.creator].filter(Boolean).join(' · ')}
                />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
      </AsyncState>

      <Box component="form" onSubmit={handleAdd} sx={{ mt: 3 }}>
        {submitError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {submitError}
          </Alert>
        )}
        <ToggleButtonGroup
          size="small"
          exclusive
          value={addMode}
          onChange={(_, mode) => mode && setAddMode(mode)}
          sx={{ mb: 1 }}
        >
          <ToggleButton value="search">Search by title</ToggleButton>
          <ToggleButton value="imdb">IMDB URL/ID</ToggleButton>
        </ToggleButtonGroup>
        <Stack direction="row" spacing={1}>
          {addMode === 'search' ? (
            <TmdbSearchAutocomplete
              search={seriesApi.search}
              value={selectedResult}
              onChange={setSelectedResult}
              label="Series title"
            />
          ) : (
            <TextField
              label="IMDB URL or tt id"
              size="small"
              value={imdbUrlOrId}
              onChange={(e) => setImdbUrlOrId(e.target.value)}
              required
              fullWidth
            />
          )}
          <Button type="submit" variant="contained" startIcon={<AddIcon />}>
            Add series
          </Button>
        </Stack>
      </Box>
    </Box>
  )
}
