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
