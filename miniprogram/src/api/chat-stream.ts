import { API_BASE_URL } from '@/config/env'
import type { ChatStreamEvent } from '@/features/chat/types'
import { apiClient } from './client'
import { ApiError } from './errors'
import { SseParser } from './sse-parser'
import { Utf8StreamDecoder } from './utf8'

interface RequestSuccess {
  statusCode: number
  data: unknown
}

interface RequestFailure {
  errMsg?: string
}

interface ChunkResult {
  data: ArrayBuffer
}

interface StreamRequestOptions {
  url: string
  method: 'POST'
  data: { question: string }
  header: Record<string, string>
  enableChunked: true
  timeout: number
  success(result: RequestSuccess): void
  fail(error: RequestFailure): void
}

interface StreamRequestTask {
  abort(): void
  onChunkReceived?(listener: (result: ChunkResult) => void): void
}

interface AuthorizedResult<T> {
  statusCode: number
  value?: T
  error?: ApiError
}

interface ChatStreamDependencies {
  baseUrl: string
  startRequest(options: StreamRequestOptions): StreamRequestTask
  runAuthorized(operation: (accessToken: string) => Promise<AuthorizedResult<void>>): Promise<void>
}

export interface StreamHandle {
  completed: Promise<void>
  abort(): void
}

export interface ChatStreamClient {
  stream(sessionId: number, question: string, onEvent: (event: ChatStreamEvent) => void): StreamHandle
}

function abortError(): Error {
  const error = new Error('aborted')
  error.name = 'AbortError'
  return error
}

function responseBytes(data: unknown): Uint8Array | null {
  if (data instanceof ArrayBuffer) return new Uint8Array(data)
  if (ArrayBuffer.isView(data)) return new Uint8Array(data.buffer, data.byteOffset, data.byteLength)
  return null
}

export function createChatStreamClient(deps: ChatStreamDependencies): ChatStreamClient {
  return {
    stream(sessionId, question, onEvent) {
      let currentTask: StreamRequestTask | undefined
      let rejectCurrent: ((error: Error) => void) | undefined
      let aborted = false

      const requestOnce = (accessToken: string): Promise<AuthorizedResult<void>> => {
        if (aborted) return Promise.reject(abortError())
        return new Promise((resolve, reject) => {
          const decoder = new Utf8StreamDecoder()
          const parser = new SseParser(onEvent)
          let receivedChunks = false
          let settled = false

          const resolveOnce = (result: AuthorizedResult<void>) => {
            if (settled) return
            settled = true
            rejectCurrent = undefined
            resolve(result)
          }
          const rejectOnce = (error: Error) => {
            if (settled) return
            settled = true
            rejectCurrent = undefined
            reject(error)
          }
          rejectCurrent = rejectOnce

          try {
            const task = deps.startRequest({
              url: `${deps.baseUrl}/chat/sessions/${sessionId}/messages/stream`,
              method: 'POST',
              data: { question },
              header: {
                Authorization: `Bearer ${accessToken}`,
                'Content-Type': 'application/json',
                Accept: 'text/event-stream',
              },
              enableChunked: true,
              timeout: 3_600_000,
              success(result) {
                if (result.statusCode < 200 || result.statusCode >= 300) {
                  resolveOnce({
                    statusCode: result.statusCode,
                    error: new ApiError('流式请求失败', result.statusCode, 'HTTP_ERROR'),
                  })
                  return
                }
                try {
                  if (receivedChunks) {
                    parser.push(decoder.finish())
                  }
                  else if (typeof result.data === 'string') {
                    parser.push(result.data)
                  }
                  else {
                    const bytes = responseBytes(result.data)
                    if (bytes) parser.push(decoder.push(bytes) + decoder.finish())
                  }
                  parser.finish()
                  resolveOnce({ statusCode: result.statusCode, value: undefined })
                }
                catch (error) {
                  rejectOnce(error instanceof Error ? error : new ApiError('流式响应解析失败'))
                }
              },
              fail(error) {
                if (aborted || error.errMsg?.toLowerCase().includes('abort')) rejectOnce(abortError())
                else rejectOnce(new ApiError(error.errMsg || '流式请求失败'))
              },
            })
            currentTask = task
            task.onChunkReceived?.((result) => {
              if (settled) return
              receivedChunks = true
              try {
                parser.push(decoder.push(new Uint8Array(result.data)))
              }
              catch (error) {
                task.abort()
                rejectOnce(error instanceof Error ? error : new ApiError('流式响应解析失败'))
              }
            })
            if (aborted) {
              task.abort()
              rejectOnce(abortError())
            }
          }
          catch (error) {
            rejectOnce(error instanceof Error ? error : new ApiError('流式请求启动失败'))
          }
        })
      }

      const completed = deps.runAuthorized(requestOnce)
      return {
        completed,
        abort() {
          if (aborted) return
          aborted = true
          currentTask?.abort()
          rejectCurrent?.(abortError())
        },
      }
    },
  }
}

export const chatStreamClient = createChatStreamClient({
  baseUrl: API_BASE_URL,
  startRequest(options) {
    return uni.request(options as UniApp.RequestOptions) as unknown as StreamRequestTask
  },
  runAuthorized: operation => apiClient.runAuthorized(operation),
})
