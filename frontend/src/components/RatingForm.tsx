import { Button, MenuItem, Select, Stack, TextField } from '@mui/material'
import { useState } from 'react'
import type { RatingScale } from '../api/types'

interface Props {
  scales: RatingScale[]
  initialQualityOptionId?: string | null
  initialSentimentOptionId?: string | null
  initialComment?: string | null
  onSave: (qualityOptionId?: string, sentimentOptionId?: string, comment?: string) => Promise<void>
}

export function RatingForm({ scales, initialQualityOptionId, initialSentimentOptionId, initialComment, onSave }: Props) {
  const quality = scales.find((s) => s.type === 'QUALITY')
  const sentiment = scales.find((s) => s.type === 'SENTIMENT')
  const [qualityOptionId, setQualityOptionId] = useState(initialQualityOptionId ?? '')
  const [sentimentOptionId, setSentimentOptionId] = useState(initialSentimentOptionId ?? '')
  const [comment, setComment] = useState(initialComment ?? '')
  const [saving, setSaving] = useState(false)

  const handleSave = async () => {
    setSaving(true)
    try {
      await onSave(qualityOptionId || undefined, sentimentOptionId || undefined, comment || undefined)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems="center">
      {quality && (
        <Select
          size="small"
          displayEmpty
          value={qualityOptionId}
          onChange={(e) => setQualityOptionId(e.target.value)}
          sx={{ minWidth: 140 }}
        >
          <MenuItem value="">
            <em>Quality</em>
          </MenuItem>
          {[...quality.options]
            .sort((a, b) => a.position - b.position)
            .map((o) => (
              <MenuItem key={o.id} value={o.id}>
                {o.label}
              </MenuItem>
            ))}
        </Select>
      )}
      {sentiment && (
        <Select
          size="small"
          displayEmpty
          value={sentimentOptionId}
          onChange={(e) => setSentimentOptionId(e.target.value)}
          sx={{ minWidth: 140 }}
        >
          <MenuItem value="">
            <em>Sentiment</em>
          </MenuItem>
          {[...sentiment.options]
            .sort((a, b) => a.position - b.position)
            .map((o) => (
              <MenuItem key={o.id} value={o.id}>
                {o.label}
              </MenuItem>
            ))}
        </Select>
      )}
      <TextField size="small" label="Comment" value={comment} onChange={(e) => setComment(e.target.value)} fullWidth />
      <Button size="small" variant="outlined" onClick={handleSave} disabled={saving}>
        Save rating
      </Button>
    </Stack>
  )
}
