import { api } from './client'
import type { Movie, MovieReview, TmdbSearchResult } from './types'

export const moviesApi = {
  list: (meetingId: string) => api.get<Movie[]>(`/meetings/${meetingId}/movies`),

  search: (query: string) => api.get<TmdbSearchResult[]>(`/movies/search?q=${encodeURIComponent(query)}`),

  add: (meetingId: string, imdbUrlOrId: string, watchLink?: string) =>
    api.post<Movie>(`/meetings/${meetingId}/movies`, { imdbUrlOrId, watchLink }),

  addByTmdbId: (meetingId: string, tmdbId: string, watchLink?: string) =>
    api.post<Movie>(`/meetings/${meetingId}/movies`, { tmdbId, watchLink }),

  update: (
    movieId: string,
    body: { customTitle?: string; preference?: string; languageCode?: string; watchLink?: string },
  ) => api.patch<Movie>(`/movies/${movieId}`, body),

  refreshMetadata: (movieId: string) => api.post<Movie>(`/movies/${movieId}/refresh-metadata`),

  move: (movieId: string, meetingId: string) => api.post<Movie>(`/movies/${movieId}/move`, { meetingId }),

  remove: (movieId: string) => api.delete<void>(`/movies/${movieId}`),

  rate: (movieId: string, qualityOptionId?: string, sentimentOptionId?: string, comment?: string) =>
    api.put<MovieReview>(`/movies/${movieId}/review`, { qualityOptionId, sentimentOptionId, comment }),

  listReviews: (movieId: string) => api.get<MovieReview[]>(`/movies/${movieId}/reviews`),
}
