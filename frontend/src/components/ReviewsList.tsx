import { Box, Stack, Typography } from '@mui/material'
import type { ClubMember, RatingScale } from '../api/types'
import { InlineRatingEditor } from './InlineRatingEditor'
import { MemberBadge } from './MemberBadge'

interface ReviewLike {
  memberId: string
  qualityOptionId: string | null
  sentimentOptionId: string | null
  comment: string | null
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
        const member = members.find((m) => m.memberId === r.memberId)
        return (
          <Stack key={r.memberId} direction="row" spacing={1.5}>
            <MemberBadge member={member} size={28} />
            <Box sx={{ borderLeft: '3px solid', borderColor: 'divider', pl: 1.5, flexGrow: 1, minWidth: 0 }}>
              {showRatings && (
                <Box sx={{ mb: r.comment ? 0.5 : 0 }}>
                  {/* Same box the Meetings table/InlineRatingEditor grid use everywhere else -- dashed border
                   * when neither rating is set, rather than a pair of Chips duplicating a different visual
                   * language for the same thing. Read-only here: editing happens via that grid, not this list. */}
                  <InlineRatingEditor
                    scales={scales}
                    memberName={member?.name ?? ''}
                    memberColor={member?.color}
                    qualityOptionId={r.qualityOptionId}
                    sentimentOptionId={r.sentimentOptionId}
                    editable={false}
                    onSaveQuality={() => {}}
                    onSaveSentiment={() => {}}
                  />
                </Box>
              )}
              {r.comment && <Typography variant="body2">{r.comment}</Typography>}
            </Box>
          </Stack>
        )
      })}
    </Stack>
  )
}
