import { describe, expect, it } from 'vitest'
import { normalizeApiBaseUrl } from './env'

describe('normalizeApiBaseUrl', () => {
  it('keeps one /api/v1 suffix and removes a trailing slash', () => {
    expect(normalizeApiBaseUrl('https://brainos.example.com/')).toBe('https://brainos.example.com/api/v1')
    expect(normalizeApiBaseUrl('https://brainos.example.com/api/v1/')).toBe('https://brainos.example.com/api/v1')
  })

  it('rejects a non-http URL', () => {
    expect(() => normalizeApiBaseUrl('javascript:alert(1)')).toThrow('API address must use HTTP or HTTPS')
  })
})
