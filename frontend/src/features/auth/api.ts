import http from '@/api/http'
import type { AuthSession } from './session'

interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  traceId: string
  timestamp: string
}

export const authApi = {
  async login(username: string, password: string): Promise<AuthSession> {
    const response = await http.post<ApiEnvelope<AuthSession>>('/auth/login', { username, password })
    return response.data.data
  },

  async refresh(refreshToken: string): Promise<AuthSession> {
    const response = await http.post<ApiEnvelope<AuthSession>>('/auth/refresh', { refreshToken })
    return response.data.data
  },

  async logout(refreshToken: string): Promise<void> {
    await http.post('/auth/logout', { refreshToken })
  },
}
