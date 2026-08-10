import { Box, MenuItem, Popover, Select, Stack, Tooltip, useMediaQuery, useTheme } from '@mui/material'
import { useState } from 'react'
import type { RatingOption, RatingScale } from '../api/types'
import { useRatingDisplay } from '../settings/RatingDisplayContext'
import { strongPastelHex } from '../utils/pastelColor'

/**
 * Quality and sentiment each get their own half of the box, filled with that rating's own color and (per the
 * user's rating-display settings, see [useRatingDisplay]) its numeric rank, its written label, or no text at all --
 * the fill color/gradient is the same regardless, only the label is affected by that third option. When both
 * halves are set, `gradientPercent` controls how much of the middle blends between the two colors (0 = hard
 * edge, colors touch directly). A half with no rating shows nothing at all (no color, no placeholder text) -- the
 * fill only ever represents a rating that was actually given. A solid 2px border in the member's strong color
 * identifies whose box this is at a glance, accompanied by column headers. Clicking (when [editable]) opens the
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
  // On a small viewport, a full written label (e.g. "Excepcional!") is too wide for the meetings table's
  // one-column-per-member layout -- fall back to just its first letter, same as `fillWith: 'number'` already
  // being a single-character rank. Only affects 'description'; 'number'/'none' are already this compact.
  const isSmallScreen = useMediaQuery(useTheme().breakpoints.down('sm'))
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
    if (fillWith === 'number') return String(rankOf(option, scale))
    return isSmallScreen ? option.label.charAt(0) : option.label
  }

  const isCompact = fillWith !== 'description' || isSmallScreen

  const textColorFor = (option: RatingOption | undefined) => {
    if (!option) return undefined
    return strongPastelHex(option.color)
  }

  const memberBorderColor = memberColor ? strongPastelHex(memberColor) : 'rgba(0, 0, 0, 0.18)'
  const tooltip = `${memberName}: ${qualityOption?.label ?? 'no quality rating'} / ${sentimentOption?.label ?? 'no sentiment rating'}`

  return (
    <>
      <Tooltip title={tooltip}>
        <Box
          onClick={editable ? (e) => setAnchorEl(e.currentTarget) : undefined}
          sx={{
            display: 'flex',
            width: isCompact ? 34 : 136,
            height: 20,
            borderRadius: 0.5,
            overflow: 'hidden',
            flexShrink: 0,
            cursor: editable ? 'pointer' : 'default',
            border: `1.75px dashed ${memberBorderColor}`,
            boxSizing: 'border-box',
            transition: 'transform 0.1s ease-in-out',
            '&:hover': editable ? { transform: 'scale(1.08)' } : {},
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
              fontSize: 10,
              fontWeight: 800,
              bgcolor: qualityOption?.color ?? 'transparent',
              color: textColorFor(qualityOption),
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
              fontSize: 10,
              fontWeight: 800,
              bgcolor: sentimentOption?.color ?? 'transparent',
              color: textColorFor(sentimentOption),
            }}
          >
            {contentFor(sentimentOption, sentiment)}
          </Box>
        </Box>
      </Tooltip>
      {editable && Boolean(anchorEl) && (
        <Popover
          open
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
                renderValue={(value) =>
                  value ? <OptionLabel option={quality.options.find((o) => o.id === value)} /> : <em>Quality</em>
                }
              >
                <MenuItem value="">
                  <em>Quality</em>
                </MenuItem>
                {[...quality.options]
                  .sort((a, b) => a.position - b.position)
                  .map((o) => (
                    <MenuItem key={o.id} value={o.id}>
                      <OptionLabel option={o} />
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
                renderValue={(value) =>
                  value ? <OptionLabel option={sentiment.options.find((o) => o.id === value)} /> : <em>Sentiment</em>
                }
              >
                <MenuItem value="">
                  <em>Sentiment</em>
                </MenuItem>
                {[...sentiment.options]
                  .sort((a, b) => a.position - b.position)
                  .map((o) => (
                    <MenuItem key={o.id} value={o.id}>
                      <OptionLabel option={o} />
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

/** A rating option's label plus a small colored dot matching its own [RatingOption.color] -- used both for each
 * dropdown [MenuItem] and (via `Select`'s `renderValue`) the closed select's own display, so the picked color is
 * visible without needing to reopen the dropdown. */
function OptionLabel({ option }: { option: RatingOption | undefined }) {
  if (!option) return null
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <Box sx={{ width: 14, height: 14, borderRadius: '50%', bgcolor: option.color, flexShrink: 0 }} />
      <span>{option.label}</span>
    </Stack>
  )
}
