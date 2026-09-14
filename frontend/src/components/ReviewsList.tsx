import { Box, Chip, Stack, Typography } from '@mui/material'
import type { ClubMember, RatingScale } from '../api/types'
import { MemberBadge } from './MemberBadge'

interface ReviewLike {
  memberId: string
  qualityOptionId: string | null
  sentimentOptionId: string | null
  comment: string | null
}

function labelFor(scales: RatingScale[], type: string, optionId: string | null) {
  if (!optionId) return null
  const scale = scales.find((s) => s.type === type)
  return scale?.options.find((o) => o.id === optionId)?.label ?? optionId
}

export function ReviewsList({
  reviews,
  scales,
  members,
  showRatings = true,
}: {
  reviews: ReviewLike[]
  scales: RatingScale[]
  members: ClubMember[]
  /** False when a caller already shows quality/sentiment some other way (e.g. MovieSection's per-member
   * InlineRatingEditor grid) and only wants this list for its comments -- rows with neither a comment nor a
   * rating to show are dropped entirely rather than rendering a bare, empty-looking badge. */
  showRatings?: boolean
}) {
  const visibleReviews = showRatings ? reviews : reviews.filter((r) => r.comment)

  if (visibleReviews.length === 0) {
    if (!showRatings) return null
    return (
      <Typography variant="body2" color="text.secondary">
        No reviews yet.
      </Typography>
    )
  }

  return (
    <Stack spacing={1}>
      {visibleReviews.map((r) => {
        const quality = showRatings ? labelFor(scales, 'QUALITY', r.qualityOptionId) : null
        const sentiment = showRatings ? labelFor(scales, 'SENTIMENT', r.sentimentOptionId) : null
        return (
          <Stack key={r.memberId} direction="row" spacing={1.5}>
            <MemberBadge member={members.find((m) => m.memberId === r.memberId)} size={28} />
            <Box sx={{ borderLeft: '3px solid', borderColor: 'divider', pl: 1.5, flexGrow: 1, minWidth: 0 }}>
              {(quality || sentiment) && (
                <Stack direction="row" spacing={1} sx={{ mb: r.comment ? 0.5 : 0, flexWrap: 'wrap' }}>
                  {quality && <Chip size="small" label={quality} />}
                  {sentiment && <Chip size="small" label={sentiment} variant="outlined" />}
                </Stack>
              )}
              {r.comment && <Typography variant="body2">{r.comment}</Typography>}
            </Box>
          </Stack>
        )
      })}
    </Stack>
  )
}
