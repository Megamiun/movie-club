import { api } from './client'
import type { Episode, EpisodeReview, Season, SeasonReview, Series, SeriesReview } from './types'

export const seriesApi = {
  list: (clubId: string) => api.get<Series[]>(`/clubs/${clubId}/series`),

  add: (clubId: string, imdbUrlOrId: string) => api.post<Series>(`/clubs/${clubId}/series`, { imdbUrlOrId }),

  get: (seriesId: string) => api.get<Series>(`/series/${seriesId}`),

  updateDisplayTitle: (seriesId: string, preference: string, customTitle?: string) =>
    api.patch<Series>(`/series/${seriesId}`, { customTitle, preference }),

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
  refreshMetadata: (episodeId: string) => api.post<Episode>(`/episodes/${episodeId}/refresh-metadata`),

  assignToMeeting: (episodeId: string, meetingId: string) =>
    api.post<Episode>(`/episodes/${episodeId}/meetings/${meetingId}`),

  unassignFromMeeting: (episodeId: string, meetingId: string) =>
    api.delete<Episode>(`/episodes/${episodeId}/meetings/${meetingId}`),

  rate: (episodeId: string, qualityOptionId?: string, sentimentOptionId?: string, comment?: string) =>
    api.put<EpisodeReview>(`/episodes/${episodeId}/review`, { qualityOptionId, sentimentOptionId, comment }),

  listForMeeting: (meetingId: string) => api.get<Episode[]>(`/meetings/${meetingId}/episodes`),
}
