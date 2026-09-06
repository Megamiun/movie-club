import { Avatar, Tooltip } from '@mui/material'
import type { ClubMember } from '../api/types'
import { useMemberPhotos } from '../settings/MemberPhotoContext'
import { contrastTextColor } from '../utils/color'
import { initials } from '../utils/members'
import { strongPastelHex } from '../utils/pastelColor'

const FALLBACK_COLOR = '#9E9E9E'

export function MemberBadge({ member, size = 20 }: { member: ClubMember | undefined; size?: number }) {
  const { showPhotos } = useMemberPhotos()
  if (!member) return <>—</>
  const color = member.color ?? FALLBACK_COLOR
  const textColor = member.color ? strongPastelHex(member.color) : contrastTextColor(FALLBACK_COLOR)

  return (
    <Tooltip title={member.name}>
      {/* A photo that fails to load (broken URL, offline) falls back to the initials below automatically -- MUI's
       * Avatar only renders `src` inside an <img>, and its children stay in the DOM underneath as the fallback
       * shown on that <img>'s error. */}
      <Avatar
        src={showPhotos ? (member.photoUrl ?? undefined) : undefined}
        sx={{ width: size, height: size, bgcolor: color, color: textColor, fontSize: size < 26 ? '0.6rem' : '0.75rem' }}
      >
        {initials(member.name)}
      </Avatar>
    </Tooltip>
  )
}
