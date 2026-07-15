import { sessionRepository } from '@/features/auth/session'

interface NavigationDeps {
  readSession(): unknown | null
  reLaunch(url: string): void
  switchTab(url: string): void
}

export function createAuthNavigation(deps: NavigationDeps) {
  return {
    restoreInitialRoute(): void {
      if (deps.readSession()) deps.switchTab('/pages/knowledge/index')
      else deps.reLaunch('/pages/login/index')
    },
    requireAuth(): boolean {
      if (deps.readSession()) return true
      deps.reLaunch('/pages/login/index')
      return false
    },
  }
}

const navigation = createAuthNavigation({
  readSession: () => sessionRepository.read(),
  reLaunch: url => uni.reLaunch({ url }),
  switchTab: url => uni.switchTab({ url }),
})

export const restoreInitialRoute = navigation.restoreInitialRoute
export const requireAuth = navigation.requireAuth
