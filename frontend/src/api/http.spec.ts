import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import { AUTH_SESSION_KEY } from '@/features/auth/session'
import http from './http'

const originalAdapter = http.defaults.adapter

afterEach(() => {
  http.defaults.adapter = originalAdapter
})

describe('HTTP client', () => {
  it('uses the API base path and attaches the stored access token as Bearer authentication', async () => {
    sessionStorage.setItem(AUTH_SESSION_KEY, JSON.stringify({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN' },
    }))
    let requestConfig: InternalAxiosRequestConfig | undefined
    http.defaults.adapter = (async (config) => {
      requestConfig = config
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
    }) as AxiosAdapter

    await http.get('/auth/me')

    expect(http.defaults.baseURL).toBe('/api/v1')
    expect(requestConfig?.headers.get('Authorization')).toBe('Bearer access-token')
  })

  it('does not attach session authentication to a cross-origin absolute URL', async () => {
    sessionStorage.setItem(AUTH_SESSION_KEY, JSON.stringify({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN' },
    }))
    let requestConfig: InternalAxiosRequestConfig | undefined
    http.defaults.adapter = (async (config) => {
      requestConfig = config
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
    }) as AxiosAdapter

    await http.get('https://evil.example/api/v1/auth/me')

    expect(requestConfig?.headers.has('Authorization')).toBe(false)
  })

  it('does not attach session authentication to a same-origin non-API URL', async () => {
    sessionStorage.setItem(AUTH_SESSION_KEY, JSON.stringify({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN' },
    }))
    let requestConfig: InternalAxiosRequestConfig | undefined
    http.defaults.adapter = (async (config) => {
      requestConfig = config
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
    }) as AxiosAdapter

    await http.get(`${window.location.origin}/health`)

    expect(requestConfig?.headers.has('Authorization')).toBe(false)
  })
})
