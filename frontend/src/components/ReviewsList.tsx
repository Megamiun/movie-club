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

/** One row per club member, always -- reserves the same space whether or not that member has rated yet, using the
 * same InlineRatingEditor box the Meetings table renders per column (dashed border when unrated). Only
 * [viewerMemberId]'s own row is editable; every other member's box is a read-only display. Its popover doubles as
 * the comment editor too (via `onSaveComment`, see InlineRatingEditor's own doc comment) -- there's no separate
 * "edit comment" control since only the viewer's own row could ever be edited anyway. */
export function ReviewsList({
  reviews,
  scales,
  members,
  viewerMemberId,
  onSaveQuality,
  onSaveSentiment,
  onSaveComment,
}: {
  reviews: ReviewLike[]
  scales: RatingScale[]
  members: ClubMember[]
  viewerMemberId: string | undefined
  onSaveQuality: (optionId: string | null) => void
  onSaveSentiment: (optionId: string | null) => void
  onSaveComment: (comment: string | null) => void | Promise<void>
}) {
  return (
    <Stack spacing={1}>
      {members.map((m) => {
        const r = reviews.find((review) => review.memberId === m.memberId)
        const isViewer = m.memberId === viewerMemberId
        return (
          <Stack key={m.memberId} direction="row" spacing={1.5}>
            <MemberBadge member={m} size={28} />
            <Box sx={{ borderLeft: '3px solid', borderColor: 'divider', pl: 1.5, flexGrow: 1, minWidth: 0 }}>
              <Box sx={{ mb: r?.comment ? 0.5 : 0 }}>
                <InlineRatingEditor
                  scales={scales}
                  memberName={m.name}
                  memberColor={m.color}
                  qualityOptionId={r?.qualityOptionId ?? null}
                  sentimentOptionId={r?.sentimentOptionId ?? null}
                  editable={isViewer}
                  onSaveQuality={onSaveQuality}
                  onSaveSentiment={onSaveSentiment}
                  {...(isViewer ? { initialComment: r?.comment, onSaveComment } : {})}
                />
              </Box>
              {r?.comment && (
                <Typography variant="body2" sx={{ whiteSpace: 'pre-line' }}>
                  {r.comment}
                </Typography>
              )}
            </Box>
          </Stack>
        )
      })}
    </Stack>
  )
}
