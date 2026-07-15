import { describe, expect, it } from 'vitest'
import { createSessionRepository } from './session'

describe('session repository', () => {
  it('round-trips a valid ADMIN session', () => {
    const values = new Map<string, unknown>()
    const repo = createSessionRepository({
      get: key => values.get(key),
      set: (key, value) => values.set(key, value),
      remove: key => values.delete(key),
    })
    const session = { accessToken: 'access', refreshToken: 'refresh', user: { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN' as const } }
    repo.write(session)
    expect(repo.read()).toEqual(session)
  })

  it('removes a structurally invalid session', () => {
    let removed = false
    const repo = createSessionRepository({
      get: () => ({ accessToken: 'only-one-field' }),
      set: () => undefined,
      remove: () => { removed = true },
    })
    expect(repo.read()).toBeNull()
    expect(removed).toBe(true)
  })
})
