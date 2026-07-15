import { defineStore } from 'pinia'
import { authApi } from './api'
import {
  clearSession,
  loadSession,
  saveSession,
  type AuthSession,
} from './session'

interface AuthState {
  session: AuthSession | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    session: loadSession(),
  }),

  getters: {
    isAuthenticated: (state): boolean => Boolean(state.session?.accessToken),
    isAdmin: (state): boolean => state.session?.user.role === 'ADMIN',
    user: (state) => state.session?.user ?? null,
  },

  actions: {
    async login(username: string, password: string): Promise<void> {
      const session = await authApi.login(username, password)
      this.session = session
      saveSession(session)
    },

    async refresh(): Promise<void> {
      if (!this.session) throw new Error('No active session')
      const session = await authApi.refresh(this.session.refreshToken)
      this.session = session
      saveSession(session)
    },

    async logout(): Promise<void> {
      const refreshToken = this.session?.refreshToken
      try {
        if (refreshToken) await authApi.logout(refreshToken)
      } catch {
        // A failed remote revocation must not leave a usable local session.
      } finally {
        this.session = null
        clearSession()
      }
    },
  },
})
