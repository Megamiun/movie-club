import { Box, MenuItem, Popover, Select, Stack, Tooltip } from '@mui/material'
import { useState } from 'react'
import type { RatingScale } from '../api/types'

/**
 * Quality + sentiment merge into one small, fixed-size pill split into each rating's own color (half and half)
 * instead of two separately-labeled chips -- a row of many members' ratings sitting right after a pick's title
 * needs to stay compact and constant-width, which variable-length label text can't do. The pill itself carries no
 * text; the full labels surface on hover via [Tooltip], and clicking it (when [editable]) still opens the same
 * quality/sentiment [Select] popover as before.
 */
export function InlineRatingEditor({
  scales,
  memberName,
  qualityOptionId,
  sentimentOptionId,
  editable,
  onSave,
}: {
  scales: RatingScale[]
  memberName: string
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

  if (!editable && !qualityOption && !sentimentOption) return null

  const background =
    qualityOption && sentimentOption
      ? `linear-gradient(90deg, ${qualityOption.color} 50%, ${sentimentOption.color} 50%)`
      : (qualityOption?.color ?? sentimentOption?.color)

  const tooltip = `${memberName}: ${qualityOption?.label ?? 'no quality rating'} / ${sentimentOption?.label ?? 'no sentiment rating'}`

  return (
    <>
      <Tooltip title={tooltip}>
        <Box
          onClick={editable ? (e) => setAnchorEl(e.currentTarget) : undefined}
          sx={{
            width: 16,
            height: 16,
            borderRadius: '50%',
            flexShrink: 0,
            cursor: editable ? 'pointer' : 'default',
            background: background ?? 'transparent',
            border: background ? 'none' : '1.5px dashed',
            borderColor: 'divider',
          }}
        />
      </Tooltip>
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
