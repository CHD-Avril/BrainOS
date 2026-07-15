import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from './api'
import { AUTH_SESSION_KEY } from './session'
import { useAuthStore } from './store'

vi.mock('./api', () => ({
  authApi: {
    login: vi.fn(),
    refresh: vi.fn(),
    logout: vi.fn(),
  },
}))

const adminSession = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  user: { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN' as const },
}

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('logs in and persists only the token pair and password-free user', async () => {
    vi.mocked(authApi.login).mockResolvedValue(adminSession)
    const store = useAuthStore()

    await store.login('admin', 'BrainOS@123')

    expect(authApi.login).toHaveBeenCalledWith('admin', 'BrainOS@123')
    expect(store.isAuthenticated).toBe(true)
    expect(store.isAdmin).toBe(true)
    expect(JSON.parse(sessionStorage.getItem(AUTH_SESSION_KEY) ?? '{}')).toEqual(adminSession)
    expect(sessionStorage.getItem(AUTH_SESSION_KEY)).not.toContain('BrainOS@123')
  })

  it('rotates both tokens when refreshing the session', async () => {
    const store = useAuthStore()
    store.session = adminSession
    const rotated = { ...adminSession, accessToken: 'access-2', refreshToken: 'refresh-2' }
    vi.mocked(authApi.refresh).mockResolvedValue(rotated)

    await store.refresh()

    expect(authApi.refresh).toHaveBeenCalledWith('refresh-1')
    expect(store.session).toEqual(rotated)
    expect(JSON.parse(sessionStorage.getItem(AUTH_SESSION_KEY) ?? '{}')).toEqual(rotated)
  })

  it('clears the local session even when remote logout fails', async () => {
    const store = useAuthStore()
    store.session = adminSession
    sessionStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(adminSession))
    vi.mocked(authApi.logout).mockRejectedValue(new Error('network unavailable'))

    await expect(store.logout()).resolves.toBeUndefined()

    expect(authApi.logout).toHaveBeenCalledWith('refresh-1')
    expect(store.session).toBeNull()
    expect(sessionStorage.getItem(AUTH_SESSION_KEY)).toBeNull()
  })

  it('discards damaged session storage without throwing', () => {
    sessionStorage.setItem(AUTH_SESSION_KEY, '{damaged-json')

    const store = useAuthStore()

    expect(store.session).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(sessionStorage.getItem(AUTH_SESSION_KEY)).toBeNull()
  })
})
