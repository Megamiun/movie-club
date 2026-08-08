import type { ClubMember } from '../api/types'

export function memberName(members: ClubMember[], memberId: string): string {
  return members.find((m) => m.memberId === memberId)?.name ?? memberId
}
