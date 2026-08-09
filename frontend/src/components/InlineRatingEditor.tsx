import { Chip, MenuItem, Popover, Select, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import type { RatingScale } from '../api/types'
import { contrastTextColor } from '../utils/color'

/**
 * Quality and sentiment are always shown as two separately-colored chips (never merged into one label) since they're
 * independent scales with their own color palettes -- see samples/img_1.png (quality) vs img_2.png (sentiment).
 * When `editable` is false (viewing another member's rating), the chips are read-only.
 */
export function InlineRatingEditor({
  scales,
  qualityOptionId,
  sentimentOptionId,
  editable,
  onSave,
}: {
  scales: RatingScale[]
  qualityOptionId: string | null
  sentimentOptionId: string | null
  editable: boolean
  onSave: (qualityOptionId?: string, sentimentOptionId?: string) => void
}) {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null)
  const quality = scales.find((s) => s.type === 'QUALITY')
  const sentiment = scales.find((s) => s.type === 'SENTIMENT')
  const qualityOption = quality?.options.find((o) => o.id === qualityOptionId)
  const sentimentOption = sentiment?.options.find((o) => o.id === sentimentOptionId)

  if (!editable && !qualityOption && !sentimentOption) {
    return (
      <Typography variant="body2" color="text.secondary">
        —
      </Typography>
    )
  }

  return (
    <>
      <Stack
        direction="row"
        spacing={0.5}
        sx={editable ? { cursor: 'pointer' } : undefined}
        onClick={editable ? (e) => setAnchorEl(e.currentTarget) : undefined}
      >
        {(editable || qualityOption) && (
          <Chip
            size="small"
            label={qualityOption?.label ?? 'Quality'}
            variant={qualityOption ? 'filled' : 'outlined'}
            sx={
              qualityOption
                ? { bgcolor: qualityOption.color, color: contrastTextColor(qualityOption.color) }
                : undefined
            }
          />
        )}
        {(editable || sentimentOption) && (
          <Chip
            size="small"
            label={sentimentOption?.label ?? 'Sentiment'}
            variant={sentimentOption ? 'filled' : 'outlined'}
            sx={
              sentimentOption
                ? { bgcolor: sentimentOption.color, color: contrastTextColor(sentimentOption.color) }
                : undefined
            }
          />
        )}
      </Stack>
      {editable && (
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
      )}
    </>
  )
}
