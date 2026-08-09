import { Box, MenuItem, Popover, Select, Stack, Tooltip } from '@mui/material'
import { useState } from 'react'
import type { RatingScale } from '../api/types'

/**
 * Quality and sentiment each get their own small square (not merged into one dot, and not full labeled chips) --
 * a middle ground that stays compact and constant-width (no variable-length label text) while still giving each
 * rating its own visible square, as opposed to a single split-color circle. The squares carry no text; the full
 * labels + member name surface on hover via [Tooltip]. A border in the member's own club color (see [memberColor])
 * around the pair identifies whose column this is at a glance, without needing a header row. Clicking (when
 * [editable]) still opens the same quality/sentiment [Select] popover as before.
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

  const tooltip = `${memberName}: ${qualityOption?.label ?? 'no quality rating'} / ${sentimentOption?.label ?? 'no sentiment rating'}`

  return (
    <>
      <Tooltip title={tooltip}>
        <Box
          onClick={editable ? (e) => setAnchorEl(e.currentTarget) : undefined}
          sx={{
            display: 'flex',
            gap: 0.5,
            p: 0.5,
            borderRadius: 1,
            flexShrink: 0,
            cursor: editable ? 'pointer' : 'default',
            border: memberColor ? `2px solid ${memberColor}` : '1px solid transparent',
          }}
        >
          <RatingSquare color={qualityOption?.color} placeholderColor={memberColor} />
          <RatingSquare color={sentimentOption?.color} placeholderColor={memberColor} />
        </Box>
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

function RatingSquare({ color, placeholderColor }: { color?: string; placeholderColor?: string | null }) {
  return (
    <Box
      sx={{
        width: 18,
        height: 18,
        borderRadius: 0.5,
        flexShrink: 0,
        bgcolor: color ?? 'transparent',
        border: color ? 'none' : '1.5px dashed',
        borderColor: color ? undefined : (placeholderColor ?? 'divider'),
      }}
    />
  )
}
