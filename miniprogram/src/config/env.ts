export function normalizeApiBaseUrl(value: string): string {
  const trimmed = value.trim().replace(/\/+$/, '')
  let parsed: URL
  try {
    parsed = new URL(trimmed)
  }
  catch {
    throw new Error('API address is invalid')
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error('API address must use HTTP or HTTPS')
  }
  const withoutApi = trimmed.endsWith('/api/v1') ? trimmed.slice(0, -7) : trimmed
  return `${withoutApi}/api/v1`
}

const configured = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
export const API_BASE_URL = normalizeApiBaseUrl(configured)
export const APP_ENV = import.meta.env.MODE
