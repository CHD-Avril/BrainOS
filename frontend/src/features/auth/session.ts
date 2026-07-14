export const AUTH_SESSION_KEY = 'brainos.auth.session'

export type UserRole = 'ADMIN' | 'USER'

export interface AuthUser {
  id: number
  username: string
  displayName: string
  role: UserRole
}

export interface AuthSession {
  accessToken: string
  refreshToken: string
  user: AuthUser
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function sanitizeSession(value: unknown): AuthSession | null {
  if (!isRecord(value) || !isRecord(value.user)) return null
  const { accessToken, refreshToken, user } = value
  const role = user.role
  if (
    typeof accessToken !== 'string' || !accessToken
    || typeof refreshToken !== 'string' || !refreshToken
    || typeof user.id !== 'number'
    || typeof user.username !== 'string' || !user.username
    || typeof user.displayName !== 'string'
    || (role !== 'ADMIN' && role !== 'USER')
  ) return null

  return {
    accessToken,
    refreshToken,
    user: {
      id: user.id,
      username: user.username,
      displayName: user.displayName,
      role,
    },
  }
}

export function loadSession(): AuthSession | null {
  const raw = window.sessionStorage.getItem(AUTH_SESSION_KEY)
  if (!raw) return null

  try {
    const session = sanitizeSession(JSON.parse(raw))
    if (!session) window.sessionStorage.removeItem(AUTH_SESSION_KEY)
    return session
  } catch {
    window.sessionStorage.removeItem(AUTH_SESSION_KEY)
    return null
  }
}

export function saveSession(session: AuthSession): void {
  window.sessionStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(session))
}

export function clearSession(): void {
  window.sessionStorage.removeItem(AUTH_SESSION_KEY)
}
