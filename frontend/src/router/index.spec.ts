import { createPinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { describe, expect, it } from 'vitest'
import { useAuthStore } from '@/features/auth/store'
import { createBrainOsRouter } from './index'

const userSession = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  user: { id: 2, username: 'user', displayName: '普通用户', role: 'USER' as const },
}

describe('authentication route guard', () => {
  it('redirects anonymous protected navigation to login and preserves the destination', async () => {
    const pinia = createPinia()
    const router = createBrainOsRouter(createMemoryHistory(), pinia)

    await router.push('/knowledge-bases?sort=recent')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/knowledge-bases?sort=recent')
  })

  it('redirects a non-admin away from an admin route', async () => {
    const pinia = createPinia()
    useAuthStore(pinia).session = userSession
    const router = createBrainOsRouter(createMemoryHistory(), pinia)

    await router.push('/admin/users')

    expect(router.currentRoute.value.name).toBe('dashboard')
  })

  it('redirects an authenticated user away from login', async () => {
    const pinia = createPinia()
    useAuthStore(pinia).session = userSession
    const router = createBrainOsRouter(createMemoryHistory(), pinia)

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('dashboard')
  })
})
