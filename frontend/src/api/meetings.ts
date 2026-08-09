import { api } from './client'
import type { Meeting, MeetingWithPicks } from './types'

export const meetingsApi = {
  list: (clubId: string) => api.get<MeetingWithPicks[]>(`/clubs/${clubId}/meetings`),

  create: (clubId: string, date: string, assignedMemberId?: string) =>
    api.post<Meeting>(`/clubs/${clubId}/meetings`, { date, assignedMemberId }),

  get: (meetingId: string) => api.get<MeetingWithPicks>(`/meetings/${meetingId}`),

  postpone: (meetingId: string, date: string) => api.patch<Meeting>(`/meetings/${meetingId}`, { date }),

  swap: (meetingId: string, otherId: string) =>
    api.post<[Meeting, Meeting]>(`/meetings/${meetingId}/swap/${otherId}`),

  merge: (meetingId: string, fromId: string) => api.post<Meeting>(`/meetings/${meetingId}/merge/${fromId}`),

  remove: (meetingId: string) => api.delete<void>(`/meetings/${meetingId}`),
}
