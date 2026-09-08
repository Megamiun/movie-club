import { Box, MenuItem, Popover, Select, Stack, Tooltip, Typography } from '@mui/material'
import { useState } from 'react'
import type { RatingOption, RatingScale } from '../api/types'
import { useRatingDisplay } from '../settings/RatingDisplayContext'
import { strongPastelHex } from '../utils/pastelColor'

/**
 * Quality and sentiment each get their own half of the box, filled with that rating's own color and (per the
 * user's rating-display settings, see [useRatingDisplay]) its numeric rank, its written label, its label's first
 * letter, or no text at all -- the fill color/gradient is the same regardless, only the label is affected by that
 * setting. `description` (full label) and `initials` (first letter only) are two separate, explicit choices, not
 * a responsive fallback -- `description` used to auto-shrink to a single letter on a small viewport (a full label
 * is too wide for the meetings table's one-column-per-member layout there), but that meant the choice wasn't
 * really the user's: verified live at the time that showing the full label really does only leave room for the
 * first member's column on a 390px phone, yet some readers may still want that trade-off deliberately (a phone
 * held landscape, or just preferring to scroll for the full word) rather than have it decided for them by screen
 * width alone. When both halves are set, `gradientPercent` controls how much of the middle blends between the two
 * colors (0 = hard edge, colors touch directly). A half with no rating shows nothing at all (no color, no
 * placeholder text) -- the fill only ever represents a rating that was actually given. A solid 2px border in the
 * member's strong color identifies whose box this is at a glance, accompanied by column headers. Clicking (when
 * [editable]) opens the same quality/sentiment [Select] popover as before.
 */
export function InlineRatingEditor({
  scales,
  memberName,
  memberColor,
  qualityOptionId,
  sentimentOptionId,
  editable,
  onSaveQuality,
  onSaveSentiment,
}: {
  scales: RatingScale[]
  memberName: string
  memberColor?: string | null
  qualityOptionId: string | null
  sentimentOptionId: string | null
  editable: boolean
  onSaveQuality: (optionId: string | null) => void
  onSaveSentiment: (optionId: string | null) => void
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
    if (fillWith === 'number') return String(rankOf(option, scale))
    if (fillWith === 'initials') return option.label.charAt(0)
    return option.label
  }

  // Sized to the *widest content this scale could ever show*, not whichever option this particular cell happens to
  // have -- every InlineRatingEditor in the table reads the same `scales`/`fillWith`, so they all compute the same
  // number here independently, which is what keeps every cell in a column the same width without lifting this into
  // a shared parent. A scale with more than 9 options needs 2 digits for its top rank (`number` mode); a longer
  // custom label needs more room in `description` mode. `none` never renders text, so it falls back to the `1`
  // floor purely to keep the box a sane non-zero size.
  const maxOptionContentLength = (scale: RatingScale | undefined): number => {
    if (!scale || fillWith === 'none') return 0
    if (fillWith === 'number') return String(scale.options.length).length
    if (fillWith === 'initials') return 1
    return Math.max(...scale.options.map((o) => o.label.length))
  }
  const maxContentLength = Math.max(1, maxOptionContentLength(quality), maxOptionContentLength(sentiment))
  const boxWidth = `calc(${maxContentLength * 2}ch + 20px)`

  const textColorFor = (option: RatingOption | undefined) => {
    if (!option) return undefined
    return strongPastelHex(option.color)
  }

  const memberBorderColor = memberColor ? strongPastelHex(memberColor) : 'rgba(0, 0, 0, 0.18)'
  const tooltip = (
    <Stack spacing={0.5} sx={{ py: 0.25 }}>
      <Typography variant="body2" sx={{ fontWeight: 700, lineHeight: 1.4 }}>
        {memberName}
      </Typography>
      {quality && <TooltipRatingLine label="Quality" option={qualityOption} />}
      {sentiment && <TooltipRatingLine label="Sentiment" option={sentimentOption} />}
    </Stack>
  )

  return (
    <>
      <Tooltip
        title={tooltip}
        slotProps={{ tooltip: { sx: { fontSize: '0.875rem', p: 1.25, maxWidth: 280 } } }}
      >
        <Box
          onClick={editable ? (e) => setAnchorEl(e.currentTarget) : undefined}
          sx={{
            display: 'flex',
            width: boxWidth,
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
                onChange={(e) => onSaveQuality(e.target.value || null)}
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
                onChange={(e) => onSaveSentiment(e.target.value || null)}
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

/** One line of the box's tooltip -- "Quality"/"Sentiment" plus a colored dot and the option's label when rated,
 * or a muted "Not rated" when not, instead of the old single-line "Member: label / label" string (which read as
 * a run-on, and rendered the same flat "no quality rating / no sentiment rating" whether one or both were unset). */
function TooltipRatingLine({ label, option }: { label: string; option: RatingOption | undefined }) {
  return (
    <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
      {option && <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: option.color, flexShrink: 0 }} />}
      <Typography variant="body2" sx={{ lineHeight: 1.4 }}>
        {label}: {option ? option.label : <Box component="span" sx={{ opacity: 0.7, fontStyle: 'italic' }}>Not rated</Box>}
      </Typography>
    </Stack>
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
