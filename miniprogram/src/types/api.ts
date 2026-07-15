export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  traceId: string
  timestamp: string
}

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export interface RawResponse {
  statusCode: number
  data: unknown
}

export interface TransportRequest {
  url: string
  method: HttpMethod
  headers: Record<string, string>
  data?: unknown
  timeoutMs?: number
}
