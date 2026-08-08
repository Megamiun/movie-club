import { Chip, Stack, Typography } from '@mui/material'
import type { ClubMember, RatingScale } from '../api/types'
import { memberName } from '../utils/members'

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
}: {
  reviews: ReviewLike[]
  scales: RatingScale[]
  members: ClubMember[]
}) {
  if (reviews.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        No reviews yet.
      </Typography>
    )
  }

  return (
    <Stack spacing={1}>
      {reviews.map((r) => {
        const quality = labelFor(scales, 'QUALITY', r.qualityOptionId)
        const sentiment = labelFor(scales, 'SENTIMENT', r.sentimentOptionId)
        return (
          <Stack key={r.memberId} direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
              {memberName(members, r.memberId)}
            </Typography>
            {quality && <Chip size="small" label={quality} />}
            {sentiment && <Chip size="small" label={sentiment} variant="outlined" />}
            {r.comment && <Typography variant="body2">{r.comment}</Typography>}
          </Stack>
        )
      })}
    </Stack>
  )
}
