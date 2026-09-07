import { api } from './client'
import type { Movie, WatchlistEntry } from './types'

export const watchlistApi = {
  list: (clubId: string) => api.get<WatchlistEntry[]>(`/clubs/${clubId}/watchlist`),

  add: (clubId: string, type: 'MOVIE' | 'SERIES', tmdbId: string) =>
    api.post<WatchlistEntry>(`/clubs/${clubId}/watchlist`, { type, tmdbId }),

  move: (entryId: string, targetPosition: number) =>
    api.post<WatchlistEntry>(`/watchlist/${entryId}/move`, { targetPosition }),

  remove: (entryId: string) => api.delete<void>(`/watchlist/${entryId}`),

  moveToMeeting: (entryId: string, meetingId: string) =>
    api.post<Movie>(`/watchlist/${entryId}/move-to-meeting/${meetingId}`),
}
