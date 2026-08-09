import LinkOffIcon from '@mui/icons-material/LinkOff'
import PlaylistAddIcon from '@mui/icons-material/PlaylistAdd'
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
  Typography,
} from '@mui/material'
import { useState, type FormEvent } from 'react'
import { episodesApi } from '../../api/series'
import { ApiError } from '../../api/client'
import type { Episode, EpisodeSearchResult, RatingScale } from '../../api/types'
import { AsyncState } from '../../components/AsyncState'
import { EpisodeSearchAutocomplete } from '../../components/EpisodeSearchAutocomplete'
import { RatingForm } from '../../components/RatingForm'
import { useAsync } from '../../hooks/useAsync'

export function EpisodeSection({
  meetingId,
  clubId,
  scales,
}: {
  meetingId: string
  clubId: string
  scales: RatingScale[]
}) {
  const { data: episodes, loading, error, reload } = useAsync(() => episodesApi.listForMeeting(meetingId), [meetingId])
  const { data: suggestions, reload: reloadSuggestions } = useAsync(() => episodesApi.nextSuggestions(clubId), [clubId])
  const [selectedEpisode, setSelectedEpisode] = useState<EpisodeSearchResult | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const handleAssign = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedEpisode) return
    setSubmitError(null)
    try {
      await episodesApi.assignToMeeting(selectedEpisode.episodeId, meetingId)
      setSelectedEpisode(null)
      reload()
      reloadSuggestions()
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleQuickAssign = async (episodeId: string) => {
    setSubmitError(null)
    try {
      await episodesApi.assignToMeeting(episodeId, meetingId)
      reload()
      reloadSuggestions()
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Episodes
      </Typography>

      <AsyncState loading={loading} error={error}>
        <Stack spacing={1}>
          {episodes?.length === 0 && <Typography color="text.secondary">No episodes scheduled yet.</Typography>}
          {episodes?.map((episode) => (
            <EpisodeItem key={episode.id} episode={episode} meetingId={meetingId} scales={scales} onChange={reload} />
          ))}
        </Stack>
      </AsyncState>

      {suggestions && suggestions.length > 0 && (
        <Stack direction="row" spacing={1} sx={{ mt: 2, flexWrap: 'wrap', alignItems: 'center' }}>
          <Typography variant="body2" color="text.secondary">
            Up next:
          </Typography>
          {suggestions.map((suggestion) => (
            <Chip
              key={suggestion.episodeId}
              size="small"
              icon={<PlaylistAddIcon fontSize="small" />}
              label={`${suggestion.seriesTitle} S${suggestion.seasonNumber}E${suggestion.episodeNumber}${suggestion.episodeTitle ? ` — ${suggestion.episodeTitle}` : ''}`}
              onClick={() => handleQuickAssign(suggestion.episodeId)}
            />
          ))}
        </Stack>
      )}

      <Box component="form" onSubmit={handleAssign} sx={{ mt: 2 }}>
        {submitError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {submitError}
          </Alert>
        )}
        <Stack direction="row" spacing={1}>
          <EpisodeSearchAutocomplete clubId={clubId} value={selectedEpisode} onChange={setSelectedEpisode} />
          <Button type="submit" variant="contained">
            Assign to this meeting
          </Button>
        </Stack>
      </Box>
    </Box>
  )
}

function EpisodeItem({
  episode,
  meetingId,
  scales,
  onChange,
}: {
  episode: Episode
  meetingId: string
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

  const handleUnassign = async () => {
    setError(null)
    try {
      await episodesApi.unassignFromMeeting(episode.id, meetingId)
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
            Ep. {episode.number}
            {episode.title ? ` — ${episode.title}` : ''}
          </Typography>
          {episode.airDate && <Chip size="small" label={episode.airDate} />}
        </Stack>
      </AccordionSummary>
      <AccordionDetails>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <Stack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            Director: {episode.director ?? '—'} &middot; Runtime:{' '}
            {episode.runtimeMinutes ? `${episode.runtimeMinutes}min` : '—'}
          </Typography>
          {episode.overview && (
            <Typography variant="body2" color="text.secondary">
              {episode.overview}
            </Typography>
          )}
          <Stack direction="row" spacing={1}>
            <IconButton size="small" onClick={handleRefresh} title="Refresh metadata">
              <RefreshIcon fontSize="small" />
            </IconButton>
            <IconButton size="small" onClick={handleUnassign} title="Unassign from meeting">
              <LinkOffIcon fontSize="small" />
            </IconButton>
          </Stack>
          <RatingForm scales={scales} onSave={handleRate} />
        </Stack>
      </AccordionDetails>
    </Accordion>
  )
}
