import { describe, expect, it, vi } from 'vitest'
import type { AuthSession } from '@/features/auth/types'
import { ApiClient } from './client'

const session: AuthSession = {
  accessToken: 'old-access', refreshToken: 'refresh',
  user: { id: 2, username: 'user', displayName: '用户', role: 'USER' },
}

describe('ApiClient', () => {
  it('injects the bearer token', async () => {
    const transport = { request: vi.fn().mockResolvedValue({ statusCode: 200, data: { code: 'OK', message: 'success', data: ['ok'], traceId: 't', timestamp: '2026-07-15T00:00:00Z' } }) }
    const client = new ApiClient({ baseUrl: 'https://api.example/api/v1', transport, sessions: { read: () => session, write: vi.fn(), clear: vi.fn() }, onExpired: vi.fn() })
    await client.request<string[]>({ method: 'GET', path: '/knowledge-bases' })
    expect(transport.request).toHaveBeenCalledWith(expect.objectContaining({ headers: { 'Content-Type': 'application/json', Authorization: 'Bearer old-access' } }))
  })

  it('performs one refresh for two simultaneous 401 responses and retries both', async () => {
    const refreshed = { ...session, accessToken: 'new-access', refreshToken: 'new-refresh' }
    let protectedCalls = 0
    const transport = { request: vi.fn(async ({ url }: { url: string }) => {
      if (url.endsWith('/auth/refresh')) return { statusCode: 200, data: { code: 'OK', message: 'success', data: refreshed, traceId: 'r', timestamp: '2026-07-15T00:00:00Z' } }
      protectedCalls += 1
      return protectedCalls <= 2
        ? { statusCode: 401, data: { code: 'UNAUTHORIZED', message: 'Authentication required', data: null, traceId: 'u', timestamp: '2026-07-15T00:00:00Z' } }
        : { statusCode: 200, data: { code: 'OK', message: 'success', data: 'ok', traceId: 's', timestamp: '2026-07-15T00:00:00Z' } }
    }) }
    const sessions = { read: () => session, write: vi.fn(), clear: vi.fn() }
    const client = new ApiClient({ baseUrl: 'https://api.example/api/v1', transport, sessions, onExpired: vi.fn() })
    await Promise.all([
      client.request({ method: 'GET', path: '/knowledge-bases' }),
      client.request({ method: 'GET', path: '/chat/sessions' }),
    ])
    expect(transport.request.mock.calls.filter(([arg]) => arg.url.endsWith('/auth/refresh'))).toHaveLength(1)
    expect(sessions.write).toHaveBeenCalledWith(refreshed)
  })
})
