import { Box, MenuItem, Popover, Select, Stack, Tooltip } from '@mui/material'
import { useState } from 'react'
import type { RatingOption, RatingScale } from '../api/types'
import { useRatingDisplay } from '../settings/RatingDisplayContext'
import { contrastTextColor } from '../utils/color'

/**
 * Quality and sentiment each get their own half of the box, filled with that rating's own color and (per the
 * user's rating-display settings, see [useRatingDisplay]) its numeric rank, its written label, or no text at all --
 * the fill color/gradient is the same regardless, only the label is affected by that third option. When both
 * halves are set, `gradientPercent` controls how much of the middle blends between the two colors (0 = hard
 * edge, colors touch directly). A half with no rating shows nothing at all (no color, no placeholder text) -- the
 * fill only ever represents a rating that was actually given. A thin dashed outline in the member's own club color
 * identifies whose box this is at a glance, without needing a header row -- always shown, even when nothing's been
 * rated yet, so there's a visible target to click even for a fully empty cell. Clicking (when [editable]) opens the
 * same quality/sentiment [Select] popover as before.
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
  const { gradientPercent, fillWith } = useRatingDisplay()
  const quality = scales.find((s) => s.type === 'QUALITY')
  const sentiment = scales.find((s) => s.type === 'SENTIMENT')
  const qualityOption = quality?.options.find((o) => o.id === qualityOptionId)
  const sentimentOption = sentiment?.options.find((o) => o.id === sentimentOptionId)

  const bothSet = Boolean(qualityOption && sentimentOption)
  const band = bothSet ? gradientPercent : 0
  const half = (100 - band) / 2

  // Rank 1 = worst (last in the scale), N = best (first) -- the option's own `position` is the reverse (0 = best).
  const rankOf = (option: RatingOption, scale: RatingScale) => scale.options.length - option.position

  const contentFor = (option: RatingOption | undefined, scale: RatingScale | undefined) => {
    if (!option || !scale || fillWith === 'none') return ''
    return fillWith === 'number' ? String(rankOf(option, scale)) : option.label
  }

  const tooltip = `${memberName}: ${qualityOption?.label ?? 'no quality rating'} / ${sentimentOption?.label ?? 'no sentiment rating'}`

  return (
    <>
      <Tooltip title={tooltip}>
        <Box
          onClick={editable ? (e) => setAnchorEl(e.currentTarget) : undefined}
          sx={{
            display: 'flex',
            width: fillWith === 'description' ? 108 : 34,
            height: 18,
            borderRadius: 0.5,
            overflow: 'hidden',
            flexShrink: 0,
            cursor: editable ? 'pointer' : 'default',
            outline: memberColor ? `1px dashed ${memberColor}` : 'none',
            outlineOffset: '1px',
          }}
        >
          <Box
            sx={{
              flex: `${half} 1 0%`,
              minWidth: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
              whiteSpace: 'nowrap',
              textOverflow: 'ellipsis',
              px: 0.25,
              fontSize: fillWith === 'number' ? 10 : 8,
              fontWeight: 700,
              bgcolor: qualityOption?.color ?? 'transparent',
              color: qualityOption ? contrastTextColor(qualityOption.color) : undefined,
            }}
          >
            {contentFor(qualityOption, quality)}
          </Box>
          {band > 0 && bothSet && (
            <Box
              sx={{
                flex: `${band} 1 0%`,
                background: `linear-gradient(90deg, ${qualityOption!.color}, ${sentimentOption!.color})`,
              }}
            />
          )}
          <Box
            sx={{
              flex: `${half} 1 0%`,
              minWidth: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
              whiteSpace: 'nowrap',
              textOverflow: 'ellipsis',
              px: 0.25,
              fontSize: fillWith === 'number' ? 10 : 8,
              fontWeight: 700,
              bgcolor: sentimentOption?.color ?? 'transparent',
              color: sentimentOption ? contrastTextColor(sentimentOption.color) : undefined,
            }}
          >
            {contentFor(sentimentOption, sentiment)}
          </Box>
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
