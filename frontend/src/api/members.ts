import { api } from './client'
import type { MemberSummary } from './types'

export const membersApi = {
  search: (query: string) => api.get<MemberSummary[]>(`/members/search?q=${encodeURIComponent(query)}`),
}
