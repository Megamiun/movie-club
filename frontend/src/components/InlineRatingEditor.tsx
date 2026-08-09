import { Chip, MenuItem, Popover, Select, Stack } from '@mui/material'
import { useState } from 'react'
import type { RatingScale } from '../api/types'

export function InlineRatingEditor({
  scales,
  qualityOptionId,
  sentimentOptionId,
  onSave,
}: {
  scales: RatingScale[]
  qualityOptionId: string | null
  sentimentOptionId: string | null
  onSave: (qualityOptionId?: string, sentimentOptionId?: string) => void
}) {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null)
  const quality = scales.find((s) => s.type === 'QUALITY')
  const sentiment = scales.find((s) => s.type === 'SENTIMENT')
  const qualityOption = quality?.options.find((o) => o.id === qualityOptionId)
  const sentimentOption = sentiment?.options.find((o) => o.id === sentimentOptionId)

  const label = [qualityOption?.label, sentimentOption?.label].filter(Boolean).join(' / ') || 'Rate'

  return (
    <>
      <Chip
        size="small"
        label={label}
        onClick={(e) => {
          e.stopPropagation()
          setAnchorEl(e.currentTarget)
        }}
        variant={qualityOption || sentimentOption ? 'filled' : 'outlined'}
        sx={qualityOption ? { bgcolor: qualityOption.color, color: '#fff' } : undefined}
      />
      <Popover
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      >
        <Stack spacing={1} sx={{ p: 1.5, minWidth: 160 }}>
          {quality && (
            <Select
              size="small"
              displayEmpty
              value={qualityOptionId ?? ''}
              onChange={(e) => onSave(e.target.value || undefined, sentimentOptionId || undefined)}
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
              value={sentimentOptionId ?? ''}
              onChange={(e) => onSave(qualityOptionId || undefined, e.target.value || undefined)}
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
        </Stack>
      </Popover>
    </>
  )
}
