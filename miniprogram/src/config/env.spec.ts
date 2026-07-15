import { describe, expect, it, vi } from 'vitest'
import { normalizeApiBaseUrl } from './env'

describe('normalizeApiBaseUrl', () => {
  it('keeps one /api/v1 suffix and removes a trailing slash', () => {
    expect(normalizeApiBaseUrl('https://brainos.example.com/')).toBe('https://brainos.example.com/api/v1')
    expect(normalizeApiBaseUrl('https://brainos.example.com/api/v1/')).toBe('https://brainos.example.com/api/v1')
  })

  it('rejects a non-http URL', () => {
    expect(() => normalizeApiBaseUrl('javascript:alert(1)')).toThrow('API address must use HTTP or HTTPS')
  })

  it('normalizes localhost without a browser URL constructor', () => {
    const originalUrl = globalThis.URL
    vi.stubGlobal('URL', undefined)
    try {
      expect(normalizeApiBaseUrl('http://localhost:8080')).toBe('http://localhost:8080/api/v1')
    }
    finally {
      vi.stubGlobal('URL', originalUrl)
    }
  })
})
