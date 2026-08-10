import { Avatar, Tooltip } from '@mui/material'
import type { ClubMember } from '../api/types'
import { contrastTextColor } from '../utils/color'
import { initials } from '../utils/members'
import { strongPastelHex } from '../utils/pastelColor'

const FALLBACK_COLOR = '#9E9E9E'

export function MemberBadge({ member, size = 20 }: { member: ClubMember | undefined; size?: number }) {
  if (!member) return <>—</>
  const color = member.color ?? FALLBACK_COLOR
  const textColor = member.color ? strongPastelHex(member.color) : contrastTextColor(FALLBACK_COLOR)

  return (
    <Tooltip title={member.name}>
      <Avatar sx={{ width: size, height: size, bgcolor: color, color: textColor, fontSize: size < 26 ? '0.6rem' : '0.75rem' }}>
        {initials(member.name)}
      </Avatar>
    </Tooltip>
  )
}
