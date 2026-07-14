import { beforeEach, describe, expect, it, vi } from 'vitest'
import http from '@/api/http'
import { authApi } from './api'

vi.mock('@/api/http', () => ({
  default: { post: vi.fn() },
}))

const tokenData = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  user: { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN' as const },
}

const tokenEnvelope = {
  code: 'OK',
  message: 'success',
  data: tokenData,
  traceId: 'trace-id',
  timestamp: '2026-07-14T00:00:00Z',
}

describe('auth API', () => {
  beforeEach(() => vi.mocked(http.post).mockReset())

  it('posts login credentials and unwraps the API envelope', async () => {
    vi.mocked(http.post).mockResolvedValue({ data: tokenEnvelope })

    await expect(authApi.login('admin', 'BrainOS@123')).resolves.toEqual(tokenData)
    expect(http.post).toHaveBeenCalledWith('/auth/login', {
      username: 'admin', password: 'BrainOS@123',
    })
  })

  it('posts the current refresh token and unwraps the rotated pair', async () => {
    vi.mocked(http.post).mockResolvedValue({ data: tokenEnvelope })

    await expect(authApi.refresh('refresh-old')).resolves.toEqual(tokenData)
    expect(http.post).toHaveBeenCalledWith('/auth/refresh', { refreshToken: 'refresh-old' })
  })

  it('posts the refresh token when logging out', async () => {
    vi.mocked(http.post).mockResolvedValue({ data: { ...tokenEnvelope, data: null } })

    await authApi.logout('refresh-token')

    expect(http.post).toHaveBeenCalledWith('/auth/logout', { refreshToken: 'refresh-token' })
  })
})
