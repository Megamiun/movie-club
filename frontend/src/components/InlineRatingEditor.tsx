import { Box, MenuItem, Popover, Select, Stack, Tooltip } from '@mui/material'
import { useState } from 'react'
import type { RatingScale } from '../api/types'

/**
 * Quality and sentiment fill the entire box -- a solid color each if only one is set, or (if both are) a gradient
 * that blends between the two only across the middle 40%-60% band, staying solid on either side. A thin border in
 * the member's own club color (see [memberColor]) identifies whose box this is at a glance, without needing a
 * header row. The box carries no text; the full labels + member name surface on hover via [Tooltip]. Clicking
 * (when [editable]) still opens the same quality/sentiment [Select] popover as before.
 */
export function InlineRatingEditor({
  scales,
  memberName,
  memberColor,
  qualityOptionId,
  sentimentOptionId,
  editable,
  onSave,
}: {
  scales: RatingScale[]
  memberName: string
  memberColor?: string | null
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

  const fill =
    qualityOption && sentimentOption
      ? `linear-gradient(90deg, ${qualityOption.color} 0%, ${qualityOption.color} 40%, ${sentimentOption.color} 60%, ${sentimentOption.color} 100%)`
      : (qualityOption?.color ?? sentimentOption?.color)

  const tooltip = `${memberName}: ${qualityOption?.label ?? 'no quality rating'} / ${sentimentOption?.label ?? 'no sentiment rating'}`

  return (
    <>
      <Tooltip title={tooltip}>
        <Box
          onClick={editable ? (e) => setAnchorEl(e.currentTarget) : undefined}
          sx={{
            width: 34,
            height: 18,
            borderRadius: 0.5,
            flexShrink: 0,
            cursor: editable ? 'pointer' : 'default',
            background: fill ?? 'transparent',
            border: fill ? 'none' : '1.5px dashed',
            borderColor: fill ? undefined : (memberColor ?? 'divider'),
            outline: fill && memberColor ? `1px solid ${memberColor}` : 'none',
            outlineOffset: '1px',
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
