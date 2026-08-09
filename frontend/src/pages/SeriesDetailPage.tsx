import AddIcon from '@mui/icons-material/Add'
import RefreshIcon from '@mui/icons-material/Refresh'
import { Alert, Box, Button, Chip, IconButton, List, ListItemButton, ListItemText, MenuItem, Select, Stack, TextField, Typography } from '@mui/material'
import { useEffect, useState, type FormEvent } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { seriesApi } from '../api/series'
import { clubsApi } from '../api/clubs'
import { ApiError } from '../api/client'
import { AsyncState } from '../components/AsyncState'
import { ImdbLink } from '../components/ImdbLink'
import { LanguagePickerDialog } from '../components/LanguagePickerDialog'
import { RatingForm } from '../components/RatingForm'
import { useAsync } from '../hooks/useAsync'
import { ratingLabel } from '../utils/rating'
import { resolveTitle } from '../utils/title'

export function SeriesDetailPage() {
  const { seriesId } = useParams<{ seriesId: string }>()
  const { data: series, loading, error, reload } = useAsync(() => seriesApi.get(seriesId!), [seriesId])
  const { data: seasons, reload: reloadSeasons } = useAsync(() => seriesApi.listSeasons(seriesId!), [seriesId])
  const { data: club } = useAsync(() => (series ? clubsApi.get(series.clubId) : Promise.resolve(null)), [
    series?.clubId,
  ])
  const { data: scales } = useAsync(
    () => (series ? clubsApi.getRatingScales(series.clubId) : Promise.resolve([])),
    [series?.clubId],
  )

  const [customTitle, setCustomTitle] = useState('')
  const [preference, setPreference] = useState<'ORIGINAL' | 'CUSTOM'>('ORIGINAL')
  const [seasonNumber, setSeasonNumber] = useState('')
  const [seasonTitle, setSeasonTitle] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    if (series) {
      setCustomTitle(series.customTitle ?? '')
      setPreference(series.displayTitlePreference === 'CUSTOM' ? 'CUSTOM' : 'ORIGINAL')
    }
  }, [series])

  const handleUpdateTitle = async () => {
    setActionError(null)
    try {
      await seriesApi.updateDisplayTitle(seriesId!, preference, customTitle || undefined)
      reload()
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handlePickLanguage = async (languageCode: string) => {
    setActionError(null)
    try {
      await seriesApi.updateDisplayTitle(seriesId!, 'LANGUAGE', undefined, languageCode)
      reload()
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleRefresh = async () => {
    setActionError(null)
    try {
      await seriesApi.refreshMetadata(seriesId!)
      reload()
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleRate = async (qualityOptionId?: string, sentimentOptionId?: string, comment?: string) => {
    await seriesApi.rate(seriesId!, qualityOptionId, sentimentOptionId, comment)
  }

  const handleAddSeason = async (event: FormEvent) => {
    event.preventDefault()
    setActionError(null)
    try {
      await seriesApi.addSeason(seriesId!, Number(seasonNumber), seasonTitle || undefined)
      setSeasonNumber('')
      setSeasonTitle('')
      reloadSeasons()
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <Box>
      <AsyncState loading={loading} error={error}>
        {series && (
          <>
            <Button component={RouterLink} to={`/clubs/${series.clubId}/series`} sx={{ mb: 2 }}>
              &larr; Back to series
            </Button>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Typography variant="h4" gutterBottom>
                {resolveTitle(series, club ?? { preferredLanguages: [], ignoredLanguages: [] })}
              </Typography>
              <ImdbLink imdbId={series.imdbId} />
            </Stack>
            <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
              {series.year && <Chip size="small" label={series.year} />}
              {series.creator && <Chip size="small" label={`Created by ${series.creator}`} />}
              {ratingLabel(series) && <Chip size="small" label={ratingLabel(series)} />}
              {series.displayTitlePreference === 'LANGUAGE' && series.displayLanguageCode && (
                <Chip size="small" label={series.displayLanguageCode} />
              )}
            </Stack>

            {actionError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {actionError}
              </Alert>
            )}

            <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', alignItems: 'center', mb: 3 }}>
              <TextField
                label="Custom title"
                size="small"
                value={customTitle}
                onChange={(e) => setCustomTitle(e.target.value)}
              />
              <Select
                size="small"
                value={preference}
                onChange={(e) => setPreference(e.target.value as 'ORIGINAL' | 'CUSTOM')}
              >
                <MenuItem value="ORIGINAL">ORIGINAL</MenuItem>
                <MenuItem value="CUSTOM">CUSTOM</MenuItem>
              </Select>
              <Button size="small" variant="outlined" onClick={handleUpdateTitle}>
                Save title
              </Button>
              <LanguagePickerDialog
                translations={series.translations}
                selectedLanguageCode={series.displayTitlePreference === 'LANGUAGE' ? series.displayLanguageCode : null}
                onSelect={handlePickLanguage}
              />
              <IconButton size="small" onClick={handleRefresh} title="Refresh metadata">
                <RefreshIcon fontSize="small" />
              </IconButton>
            </Stack>

            <Typography variant="subtitle1" gutterBottom>
              Your rating
            </Typography>
            <RatingForm scales={scales ?? []} onSave={handleRate} />

            <Typography variant="h6" sx={{ mt: 4 }} gutterBottom>
              Seasons
            </Typography>
            <List>
              {seasons?.length === 0 && <Typography color="text.secondary">No seasons yet.</Typography>}
              {seasons
                ?.sort((a, b) => a.number - b.number)
                .map((season) => (
                  <ListItemButton
                    key={season.id}
                    component={RouterLink}
                    to={`/seasons/${season.id}?seriesId=${series.id}`}
                    divider
                  >
                    <ListItemText primary={`Season ${season.number}${season.title ? ` — ${season.title}` : ''}`} />
                  </ListItemButton>
                ))}
            </List>

            <Box component="form" onSubmit={handleAddSeason} sx={{ mt: 2 }}>
              <Stack direction="row" spacing={1}>
                <TextField
                  label="Number"
                  type="number"
                  size="small"
                  value={seasonNumber}
                  onChange={(e) => setSeasonNumber(e.target.value)}
                  required
                  sx={{ width: 100 }}
                />
                <TextField
                  label="Title (optional)"
                  size="small"
                  value={seasonTitle}
                  onChange={(e) => setSeasonTitle(e.target.value)}
                  fullWidth
                />
                <Button type="submit" variant="contained" startIcon={<AddIcon />}>
                  Add season
                </Button>
              </Stack>
            </Box>
          </>
        )}
      </AsyncState>
    </Box>
  )
}
