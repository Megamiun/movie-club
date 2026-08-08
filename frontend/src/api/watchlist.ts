import { api } from './client'
import type { WatchlistEntry } from './types'

export const watchlistApi = {
  list: (clubId: string) => api.get<WatchlistEntry[]>(`/clubs/${clubId}/watchlist`),

  add: (clubId: string, title: string, imdbUrl?: string, notes?: string) =>
    api.post<WatchlistEntry>(`/clubs/${clubId}/watchlist`, { title, imdbUrl, notes }),

  update: (entryId: string, body: { title?: string; imdbUrl?: string; notes?: string }) =>
    api.patch<WatchlistEntry>(`/watchlist/${entryId}`, body),

  remove: (entryId: string) => api.delete<void>(`/watchlist/${entryId}`),
}
