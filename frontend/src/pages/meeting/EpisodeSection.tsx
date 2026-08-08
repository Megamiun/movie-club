import LinkOffIcon from '@mui/icons-material/LinkOff'
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
import { episodesApi } from '../../api/series'
import { ApiError } from '../../api/client'
import type { Episode, RatingScale } from '../../api/types'
import { AsyncState } from '../../components/AsyncState'
import { RatingForm } from '../../components/RatingForm'
import { useAsync } from '../../hooks/useAsync'

export function EpisodeSection({ meetingId, scales }: { meetingId: string; scales: RatingScale[] }) {
  const { data: episodes, loading, error, reload } = useAsync(() => episodesApi.listForMeeting(meetingId), [meetingId])
  const [episodeId, setEpisodeId] = useState('')
  const [submitError, setSubmitError] = useState<string | null>(null)

  const handleAssign = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitError(null)
    try {
      await episodesApi.assignToMeeting(episodeId, meetingId)
      setEpisodeId('')
      reload()
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

      <Box component="form" onSubmit={handleAssign} sx={{ mt: 2 }}>
        {submitError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {submitError}
          </Alert>
        )}
        <Stack direction="row" spacing={1}>
          <TextField
            label="Episode ID"
            size="small"
            value={episodeId}
            onChange={(e) => setEpisodeId(e.target.value)}
            required
            fullWidth
          />
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
