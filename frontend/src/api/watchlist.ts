import { api } from './client'
import type { Movie, WatchlistEntry } from './types'

export const watchlistApi = {
  list: (clubId: string) => api.get<WatchlistEntry[]>(`/clubs/${clubId}/watchlist`),

  add: (clubId: string, type: 'MOVIE' | 'SERIES', tmdbId: string) =>
    api.post<WatchlistEntry>(`/clubs/${clubId}/watchlist`, { type, tmdbId }),

  move: (entryId: string, position: number) => api.patch<WatchlistEntry>(`/watchlist/${entryId}`, { position }),

  remove: (entryId: string) => api.delete<void>(`/watchlist/${entryId}`),

  moveToMeeting: (entryId: string, meetingId: string) =>
    api.post<Movie>(`/meetings/${meetingId}/movies`, { fromWatchlistEntryId: entryId }),
}
