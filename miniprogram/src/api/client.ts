import { API_BASE_URL } from '@/config/env'
import { sessionRepository } from '@/features/auth/session'
import type { AuthSession } from '@/features/auth/types'
import type { ApiEnvelope, HttpMethod, RawResponse, TransportRequest } from '@/types/api'
import { ApiError } from './errors'
import { uniRequestTransport } from './transport'

interface SessionGateway {
  read(): AuthSession | null
  write(value: AuthSession): void
  clear(): void
}

interface Transport {
  request(options: TransportRequest): Promise<RawResponse>
}

interface ClientOptions {
  baseUrl: string
  transport: Transport
  sessions: SessionGateway
  onExpired(): void
}

interface RequestOptions {
  method: HttpMethod
  path: string
  data?: unknown
  timeoutMs?: number
  authenticated?: boolean
}

function envelope<T>(response: RawResponse): ApiEnvelope<T> {
  const body = response.data as Partial<ApiEnvelope<T>> | null
  if (!body || typeof body !== 'object' || typeof body.code !== 'string') {
    throw new ApiError('服务端响应格式错误', response.statusCode, 'INVALID_RESPONSE')
  }
  if (response.statusCode < 200 || response.statusCode >= 300 || body.code !== 'OK') {
    throw new ApiError(body.message || '请求失败', response.statusCode, body.code, body.traceId || '')
  }
  return body as ApiEnvelope<T>
}

export class ApiClient {
  private refreshInFlight?: Promise<AuthSession>

  constructor(private readonly options: ClientOptions) {}

  async request<T>(options: RequestOptions): Promise<T> {
    return this.execute<T>(options, true)
  }

  async runAuthorized<T>(operation: (accessToken: string) => Promise<{ statusCode: number; value?: T; error?: ApiError }>): Promise<T> {
    const session = this.options.sessions.read()
    if (!session) throw new ApiError('请先登录', 401, 'UNAUTHORIZED')
    let result = await operation(session.accessToken)
    if (result.statusCode === 401) {
      const refreshed = await this.refresh(session.refreshToken)
      result = await operation(refreshed.accessToken)
    }
    if (result.statusCode < 200 || result.statusCode >= 300) {
      throw result.error ?? new ApiError('请求失败', result.statusCode, 'HTTP_ERROR')
    }
    return result.value as T
  }

  private async execute<T>(options: RequestOptions, mayRefresh: boolean): Promise<T> {
    const session = this.options.sessions.read()
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (options.authenticated !== false && session?.accessToken) headers.Authorization = `Bearer ${session.accessToken}`
    const response = await this.options.transport.request({
      url: `${this.options.baseUrl}${options.path}`,
      method: options.method,
      headers,
      data: options.data,
      timeoutMs: options.timeoutMs,
    })
    if (response.statusCode === 401 && mayRefresh && session?.refreshToken) {
      await this.refresh(session.refreshToken)
      return this.execute<T>(options, false)
    }
    return envelope<T>(response).data
  }

  private refresh(refreshToken: string): Promise<AuthSession> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.options.transport.request({
        url: `${this.options.baseUrl}/auth/refresh`,
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        data: { refreshToken },
      }).then(response => envelope<AuthSession>(response).data)
        .then((session) => {
          this.options.sessions.write(session)
          return session
        })
        .catch((error) => {
          this.options.sessions.clear()
          this.options.onExpired()
          throw error
        })
        .finally(() => { this.refreshInFlight = undefined })
    }
    return this.refreshInFlight
  }
}

export const apiClient = new ApiClient({
  baseUrl: API_BASE_URL,
  transport: uniRequestTransport,
  sessions: sessionRepository,
  onExpired: () => uni.reLaunch({ url: '/pages/login/index' }),
})
