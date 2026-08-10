import { api } from './client'
import type { WatchlistEntry } from './types'

export const watchlistApi = {
  list: (clubId: string) => api.get<WatchlistEntry[]>(`/clubs/${clubId}/watchlist`),

  add: (clubId: string, type: 'MOVIE' | 'SERIES', tmdbId: string) =>
    api.post<WatchlistEntry>(`/clubs/${clubId}/watchlist`, { type, tmdbId }),

  move: (entryId: string, direction: 'UP' | 'DOWN') =>
    api.post<WatchlistEntry>(`/watchlist/${entryId}/move`, { direction }),

  remove: (entryId: string) => api.delete<void>(`/watchlist/${entryId}`),
}
