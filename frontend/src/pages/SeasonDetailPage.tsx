import AddIcon from '@mui/icons-material/Add'
import RefreshIcon from '@mui/icons-material/Refresh'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Chip,
  IconButton,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useState, type FormEvent } from 'react'
import { Link as RouterLink, useParams, useSearchParams } from 'react-router-dom'
import { seasonsApi, episodesApi, seriesApi } from '../api/series'
import { clubsApi } from '../api/clubs'
import { ApiError } from '../api/client'
import type { Episode, RatingScale } from '../api/types'
import { AsyncState } from '../components/AsyncState'
import { ImdbLink } from '../components/ImdbLink'
import { RatingForm } from '../components/RatingForm'
import { useAsync } from '../hooks/useAsync'
import { digitsOf } from '../hooks/useSeasonNumbers'
import { episodeCode } from '../utils/episode'
import { resolveTitle } from '../utils/title'

export function SeasonDetailPage() {
  const { seasonId } = useParams<{ seasonId: string }>()
  const [searchParams] = useSearchParams()
  const seriesId = searchParams.get('seriesId')

  const { data: episodes, loading, error, reload } = useAsync(() => seasonsApi.listEpisodes(seasonId!), [seasonId])
  const { data: siblingSeasons } = useAsync(() => seasonsApi.listSiblings(seasonId!), [seasonId])
  const season = siblingSeasons?.find((s) => s.id === seasonId)
  const seasonDigits = siblingSeasons?.length ? digitsOf(Math.max(...siblingSeasons.map((s) => s.number))) : undefined
  const episodeDigits = episodes?.length ? digitsOf(Math.max(...episodes.map((e) => e.number))) : undefined
  const { data: series } = useAsync(() => (seriesId ? seriesApi.get(seriesId) : Promise.resolve(null)), [seriesId])
  const { data: club } = useAsync(() => (series ? clubsApi.get(series.clubId) : Promise.resolve(null)), [
    series?.clubId,
  ])
  const { data: scales } = useAsync(
    () => (series ? clubsApi.getRatingScales(series.clubId) : Promise.resolve([])),
    [series?.clubId],
  )

  const [episodeNumber, setEpisodeNumber] = useState('')
  const [episodeTitle, setEpisodeTitle] = useState('')
  const [meetingId, setMeetingId] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const handleAddEpisode = async (event: FormEvent) => {
    event.preventDefault()
    setActionError(null)
    try {
      await seasonsApi.addEpisode(seasonId!, Number(episodeNumber), episodeTitle || undefined, meetingId || undefined)
      setEpisodeNumber('')
      setEpisodeTitle('')
      setMeetingId('')
      reload()
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleRateSeason = async (qualityOptionId?: string, sentimentOptionId?: string, comment?: string) => {
    await seasonsApi.rate(seasonId!, qualityOptionId, sentimentOptionId, comment)
  }

  return (
    <Box>
      <AsyncState loading={loading} error={error}>
        {series && (
          <Button component={RouterLink} to={`/series/${series.id}`} sx={{ mb: 2 }}>
            &larr; Back to {resolveTitle(series, club ?? { preferredLanguages: [], ignoredLanguages: [] })}
          </Button>
        )}
        <Typography variant="h4" gutterBottom>
          Season
        </Typography>

        {actionError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {actionError}
          </Alert>
        )}

        <Typography variant="subtitle1" gutterBottom>
          Your rating
        </Typography>
        <RatingForm scales={scales ?? []} onSave={handleRateSeason} />

        <Typography variant="h6" sx={{ mt: 4 }} gutterBottom>
          Episodes
        </Typography>
        <Stack spacing={1}>
          {episodes?.length === 0 && <Typography color="text.secondary">No episodes yet.</Typography>}
          {episodes
            ?.sort((a, b) => a.number - b.number)
            .map((episode) => (
              <EpisodeRow
                key={episode.id}
                episode={episode}
                seasonNumber={season?.number}
                seasonDigits={seasonDigits}
                episodeDigits={episodeDigits}
                scales={scales ?? []}
                onChange={reload}
              />
            ))}
        </Stack>

        <Box component="form" onSubmit={handleAddEpisode} sx={{ mt: 3 }}>
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
            <TextField
              label="Number"
              type="number"
              size="small"
              value={episodeNumber}
              onChange={(e) => setEpisodeNumber(e.target.value)}
              required
              sx={{ width: 100 }}
            />
            <TextField
              label="Title (optional)"
              size="small"
              value={episodeTitle}
              onChange={(e) => setEpisodeTitle(e.target.value)}
            />
            <TextField
              label="Meeting ID (optional)"
              size="small"
              value={meetingId}
              onChange={(e) => setMeetingId(e.target.value)}
            />
            <Button type="submit" variant="contained" startIcon={<AddIcon />}>
              Add episode
            </Button>
          </Stack>
        </Box>
      </AsyncState>
    </Box>
  )
}

function EpisodeRow({
  episode,
  seasonNumber,
  seasonDigits,
  episodeDigits,
  scales,
  onChange,
}: {
  episode: Episode
  seasonNumber: number | undefined
  seasonDigits: number | undefined
  episodeDigits: number | undefined
  scales: RatingScale[]
  onChange: () => void
}) {
  const [error, setError] = useState<string | null>(null)

  const handleRefresh = async () => {
    setError(null)
    try {
      await episodesApi.refreshMetadata(episode.id)
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleRate = async (qualityOptionId?: string, sentimentOptionId?: string, comment?: string) => {
    await episodesApi.rate(episode.id, qualityOptionId, sentimentOptionId, comment)
  }

  return (
    <Accordion>
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexGrow: 1 }}>
          <Typography sx={{ flexGrow: 1 }}>
            {episodeCode(seasonNumber, episode.number, seasonDigits, episodeDigits)}
            {episode.title ? ` — ${episode.title}` : ''}
          </Typography>
          {episode.airDate && <Chip size="small" label={episode.airDate} />}
          {episode.imdbId && <ImdbLink imdbId={episode.imdbId} />}
        </Stack>
      </AccordionSummary>
      <AccordionDetails>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <Stack spacing={2}>
          {episode.overview && (
            <Typography variant="body2" color="text.secondary">
              {episode.overview}
            </Typography>
          )}
          <IconButton size="small" onClick={handleRefresh} title="Refresh metadata" sx={{ alignSelf: 'flex-start' }}>
            <RefreshIcon fontSize="small" />
          </IconButton>
          <RatingForm scales={scales} onSave={handleRate} />
        </Stack>
      </AccordionDetails>
    </Accordion>
  )
}
