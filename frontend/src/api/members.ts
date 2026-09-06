import { api } from './client'
import type { Member, MemberSummary } from './types'

export const membersApi = {
  search: (query: string) => api.get<MemberSummary[]>(`/members/search?q=${encodeURIComponent(query)}`),

  uploadPhoto: (memberId: string, file: File) => {
    const form = new FormData()
    form.append('photo', file)
    return api.postForm<Member>(`/members/${memberId}/photo`, form)
  },
}
