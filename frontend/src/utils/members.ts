import type { ClubMember } from '../api/types'

export function memberName(members: ClubMember[], memberId: string): string {
  return members.find((m) => m.memberId === memberId)?.name ?? memberId
}

/** Up to 2 letters, one per word (e.g. "Jane Doe" -> "JD", "Cher" -> "C") -- used for the meetings table's
 * colored chosen-by badge, where a full name doesn't fit. */
export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('')
}
