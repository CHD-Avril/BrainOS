import type { AuthSession } from './types'

const SESSION_KEY = 'brainos.auth.session'

interface StorageGateway {
  get(key: string): unknown
  set(key: string, value: unknown): unknown
  remove(key: string): unknown
}

export interface SessionRepository {
  read(): AuthSession | null
  write(value: AuthSession): void
  clear(): void
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isAuthSession(value: unknown): value is AuthSession {
  if (!isRecord(value) || !isRecord(value.user)) return false
  const user = value.user
  return typeof value.accessToken === 'string'
    && typeof value.refreshToken === 'string'
    && typeof user.id === 'number'
    && Number.isFinite(user.id)
    && typeof user.username === 'string'
    && typeof user.displayName === 'string'
    && (user.role === 'ADMIN' || user.role === 'USER')
}

export function createSessionRepository(storage: StorageGateway): SessionRepository {
  return {
    read() {
      const value = storage.get(SESSION_KEY)
      if (isAuthSession(value)) return value
      if (value !== undefined && value !== null && value !== '') storage.remove(SESSION_KEY)
      return null
    },
    write(value) {
      storage.set(SESSION_KEY, value)
    },
    clear() {
      storage.remove(SESSION_KEY)
    },
  }
}

export const sessionRepository = createSessionRepository({
  get: key => uni.getStorageSync(key),
  set: (key, value) => uni.setStorageSync(key, value),
  remove: key => uni.removeStorageSync(key),
})
