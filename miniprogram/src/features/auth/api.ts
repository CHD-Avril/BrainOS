import { apiClient } from '@/api/client'
import type { AuthSession, AuthUser } from './types'

export const authApi = {
  login(username: string, password: string): Promise<AuthSession> {
    return apiClient.request({ method: 'POST', path: '/auth/login', data: { username, password }, authenticated: false })
  },
  logout(refreshToken: string): Promise<void> {
    return apiClient.request({ method: 'POST', path: '/auth/logout', data: { refreshToken } })
  },
  me(): Promise<AuthUser> {
    return apiClient.request({ method: 'GET', path: '/auth/me' })
  },
}
