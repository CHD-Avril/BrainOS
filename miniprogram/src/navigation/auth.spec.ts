import { describe, expect, it, vi } from 'vitest'
import { createAuthNavigation } from './auth'

describe('auth navigation', () => {
  it('keeps an anonymous startup on the initial login page', () => {
    const reLaunch = vi.fn()
    const navigation = createAuthNavigation({ readSession: () => null, reLaunch, switchTab: vi.fn() })
    navigation.restoreInitialRoute()
    expect(reLaunch).not.toHaveBeenCalled()
  })

  it('redirects an anonymous user to login', () => {
    const reLaunch = vi.fn()
    const navigation = createAuthNavigation({ readSession: () => null, reLaunch, switchTab: vi.fn() })
    expect(navigation.requireAuth()).toBe(false)
    expect(reLaunch).toHaveBeenCalledWith('/pages/login/index')
  })

  it('opens the knowledge tab for a restored session', () => {
    const switchTab = vi.fn()
    const navigation = createAuthNavigation({ readSession: () => ({ accessToken: 'a' }), reLaunch: vi.fn(), switchTab })
    navigation.restoreInitialRoute()
    expect(switchTab).toHaveBeenCalledWith('/pages/knowledge/index')
  })
})
