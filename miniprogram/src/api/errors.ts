export class ApiError extends Error {
  constructor(
    message: string,
    readonly statusCode = 0,
    readonly code = 'NETWORK_ERROR',
    readonly traceId = '',
  ) {
    super(message)
    this.name = 'ApiError'
  }
}
