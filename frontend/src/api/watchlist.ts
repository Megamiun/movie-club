import { api } from './client'
import type { WatchlistEntry } from './types'

export const watchlistApi = {
  list: (clubId: string) => api.get<WatchlistEntry[]>(`/clubs/${clubId}/watchlist`),

  add: (clubId: string, type: 'MOVIE' | 'SERIES', tmdbId: string, notes?: string) =>
    api.post<WatchlistEntry>(`/clubs/${clubId}/watchlist`, { type, tmdbId, notes }),

  update: (entryId: string, notes?: string) => api.patch<WatchlistEntry>(`/watchlist/${entryId}`, { notes }),

  move: (entryId: string, direction: 'UP' | 'DOWN') =>
    api.post<WatchlistEntry>(`/watchlist/${entryId}/move`, { direction }),

  remove: (entryId: string) => api.delete<void>(`/watchlist/${entryId}`),
}
