import { api } from './client'
import type { Club, ClubDetail, ClubMember, RatingScale } from './types'

export const clubsApi = {
  list: () => api.get<Club[]>('/clubs'),

  create: (name: string) => api.post<ClubDetail>('/clubs', { name }),

  get: (clubId: string) => api.get<ClubDetail>(`/clubs/${clubId}`),

  addMember: (clubId: string, memberId: string, role: string) =>
    api.post<ClubMember>(`/clubs/${clubId}/members`, { memberId, role }),

  changeRole: (clubId: string, memberId: string, role: string) =>
    api.patch<ClubMember>(`/clubs/${clubId}/members/${memberId}`, { role }),

  removeMember: (clubId: string, memberId: string) => api.delete<void>(`/clubs/${clubId}/members/${memberId}`),

  updateRotation: (clubId: string, memberIds: string[]) =>
    api.put<void>(`/clubs/${clubId}/rotation`, { memberIds }),

  getRatingScales: (clubId: string) => api.get<RatingScale[]>(`/clubs/${clubId}/rating-scales`),
}
