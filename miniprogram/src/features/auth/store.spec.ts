import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './store'

const { session, authApi, sessionRepository } = vi.hoisted(() => ({
  session: { accessToken: 'a', refreshToken: 'r', user: { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN' as const } },
  authApi: { login: vi.fn(), logout: vi.fn() },
  sessionRepository: { read: vi.fn(), write: vi.fn(), clear: vi.fn() },
}))
vi.mock('./api', () => ({ authApi }))
vi.mock('./session', () => ({ sessionRepository }))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionRepository.read.mockReturnValue(null)
    authApi.login.mockResolvedValue(session)
    authApi.logout.mockRejectedValue(new Error('offline'))
  })

  it('persists a successful login', async () => {
    const store = useAuthStore()
    await store.login('admin', 'secret')
    expect(store.session).toEqual(session)
    expect(store.isAuthenticated).toBe(true)
  })

  it('clears local state even when remote logout fails', async () => {
    const store = useAuthStore()
    store.session = session
    await store.logout()
    expect(store.session).toBeNull()
  })
})
