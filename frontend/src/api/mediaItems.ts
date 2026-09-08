import { api } from './client'
import type { MediaItem } from './types'

export const mediaItemsApi = {
  refreshMetadata: (mediaItemId: string) => api.post<MediaItem>(`/media-items/${mediaItemId}/refresh-metadata`),
}
