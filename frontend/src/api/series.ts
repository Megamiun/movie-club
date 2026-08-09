import { api } from './client'
import type {
  Episode,
  EpisodeReview,
  EpisodeSearchResult,
  Season,
  SeasonReview,
  Series,
  SeriesReview,
  TmdbSearchResult,
} from './types'

export const seriesApi = {
  list: (clubId: string) => api.get<Series[]>(`/clubs/${clubId}/series`),

  search: (query: string) => api.get<TmdbSearchResult[]>(`/series/search?q=${encodeURIComponent(query)}`),

  add: (clubId: string, imdbUrlOrId: string) => api.post<Series>(`/clubs/${clubId}/series`, { imdbUrlOrId }),

  addByTmdbId: (clubId: string, tmdbId: string) => api.post<Series>(`/clubs/${clubId}/series`, { tmdbId }),

  get: (seriesId: string) => api.get<Series>(`/series/${seriesId}`),

  updateDisplayTitle: (seriesId: string, preference: string, customTitle?: string, languageCode?: string) =>
    api.patch<Series>(`/series/${seriesId}`, { customTitle, preference, languageCode }),

  refreshMetadata: (seriesId: string) => api.post<Series>(`/series/${seriesId}/refresh-metadata`),

  rate: (seriesId: string, qualityOptionId?: string, sentimentOptionId?: string, comment?: string) =>
    api.put<SeriesReview>(`/series/${seriesId}/review`, { qualityOptionId, sentimentOptionId, comment }),

  listSeasons: (seriesId: string) => api.get<Season[]>(`/series/${seriesId}/seasons`),

  addSeason: (seriesId: string, number: number, title?: string) =>
    api.post<Season>(`/series/${seriesId}/seasons`, { number, title }),
}

export const seasonsApi = {
  rate: (seasonId: string, qualityOptionId?: string, sentimentOptionId?: string, comment?: string) =>
    api.put<SeasonReview>(`/seasons/${seasonId}/review`, { qualityOptionId, sentimentOptionId, comment }),

  listEpisodes: (seasonId: string) => api.get<Episode[]>(`/seasons/${seasonId}/episodes`),

  addEpisode: (seasonId: string, number: number, title?: string, meetingId?: string) =>
    api.post<Episode>(`/seasons/${seasonId}/episodes`, { number, title, meetingId }),
}

export const episodesApi = {
  search: (clubId: string, query: string) =>
    api.get<EpisodeSearchResult[]>(`/clubs/${clubId}/episodes/search?q=${encodeURIComponent(query)}`),

  nextSuggestions: (clubId: string) =>
    api.get<EpisodeSearchResult[]>(`/clubs/${clubId}/episodes/next-suggestions`),

  refreshMetadata: (episodeId: string) => api.post<Episode>(`/episodes/${episodeId}/refresh-metadata`),

  assignToMeeting: (episodeId: string, meetingId: string) =>
    api.post<Episode>(`/episodes/${episodeId}/meetings/${meetingId}`),

  unassignFromMeeting: (episodeId: string, meetingId: string) =>
    api.delete<Episode>(`/episodes/${episodeId}/meetings/${meetingId}`),

  rate: (episodeId: string, qualityOptionId?: string, sentimentOptionId?: string, comment?: string) =>
    api.put<EpisodeReview>(`/episodes/${episodeId}/review`, { qualityOptionId, sentimentOptionId, comment }),

  listForMeeting: (meetingId: string) => api.get<Episode[]>(`/meetings/${meetingId}/episodes`),
}
