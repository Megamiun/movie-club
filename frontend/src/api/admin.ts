import { api } from './client'
import type { AdminMediaItem, AdminUser } from './types'

export const adminApi = {
  listUsers: () => api.get<AdminUser[]>('/admin/users'),

  listMediaItems: () => api.get<AdminMediaItem[]>('/admin/media-items'),
}
