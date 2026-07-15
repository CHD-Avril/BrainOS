import { defineStore } from 'pinia'
import { authApi } from './api'
import { sessionRepository } from './session'
import type { AuthSession } from './types'

export const useAuthStore = defineStore('auth', {
  state: (): { session: AuthSession | null; submitting: boolean; error: string } => ({
    session: sessionRepository.read(),
    submitting: false,
    error: '',
  }),
  getters: {
    isAuthenticated: state => Boolean(state.session?.accessToken),
    isAdmin: state => state.session?.user.role === 'ADMIN',
  },
  actions: {
    async login(username: string, password: string) {
      this.submitting = true
      this.error = ''
      try {
        const session = await authApi.login(username, password)
        sessionRepository.write(session)
        this.session = session
      }
      catch (error) {
        this.error = error instanceof Error ? error.message : '登录失败'
        throw error
      }
      finally {
        this.submitting = false
      }
    },
    async logout() {
      const refreshToken = this.session?.refreshToken
      try {
        if (refreshToken) await authApi.logout(refreshToken)
      }
      catch {
        // Local revocation remains mandatory when the device is offline.
      }
      finally {
        sessionRepository.clear()
        this.session = null
      }
    },
  },
})
