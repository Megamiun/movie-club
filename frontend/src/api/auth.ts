import { api } from './client'
import type { AuthResponse } from './types'

export const authApi = {
  register: (inviteToken: string, name: string, password: string) =>
    api.post<AuthResponse>('/auth/register', { inviteToken, name, password }),

  login: (email: string, password: string) => api.post<AuthResponse>('/auth/login', { email, password }),

  invite: (email: string) => api.post<{ inviteToken: string }>('/auth/invite', { email }),
}
