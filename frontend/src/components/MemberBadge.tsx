import { Avatar, Tooltip } from '@mui/material'
import type { ClubMember } from '../api/types'
import { contrastTextColor } from '../utils/color'
import { initials } from '../utils/members'

const FALLBACK_COLOR = '#9E9E9E'

export function MemberBadge({ member }: { member: ClubMember | undefined }) {
  if (!member) return <>—</>
  const color = member.color ?? FALLBACK_COLOR

  return (
    <Tooltip title={member.name}>
      <Avatar sx={{ width: 28, height: 28, bgcolor: color, color: contrastTextColor(color), fontSize: '0.75rem' }}>
        {initials(member.name)}
      </Avatar>
    </Tooltip>
  )
}
