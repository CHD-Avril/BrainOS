export function normalizeApiBaseUrl(value: string): string {
  const trimmed = value.trim().replace(/\/+$/, '')
  const scheme = trimmed.match(/^([a-z][a-z\d+.-]*):/i)?.[1]?.toLowerCase()
  if (scheme && scheme !== 'http' && scheme !== 'https') {
    throw new Error('API address must use HTTP or HTTPS')
  }
  const match = trimmed.match(/^https?:\/\/([^/?#\s]+)(?:\/[^?#\s]*)?$/i)
  if (!match || !validAuthority(match[1]!)) throw new Error('API address is invalid')
  const withoutApi = trimmed.endsWith('/api/v1') ? trimmed.slice(0, -7) : trimmed
  return `${withoutApi}/api/v1`
}

function validAuthority(authority: string): boolean {
  if (authority.includes('@') || authority.includes('\\')) return false
  if (authority.startsWith('[')) {
    const ipv6 = authority.match(/^\[[0-9a-f:.]+\](?::(\d{1,5}))?$/i)
    return Boolean(ipv6) && validPort(ipv6?.[1])
  }
  const hostAndPort = authority.match(/^([a-z\d.-]+)(?::(\d{1,5}))?$/i)
  if (!hostAndPort || !hostAndPort[1] || hostAndPort[1].startsWith('.') || hostAndPort[1].endsWith('.')) return false
  return validPort(hostAndPort[2])
}

function validPort(value: string | undefined): boolean {
  return value === undefined || Number(value) <= 65_535
}

const configured = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
export const API_BASE_URL = normalizeApiBaseUrl(configured)
export const APP_ENV = import.meta.env.MODE
