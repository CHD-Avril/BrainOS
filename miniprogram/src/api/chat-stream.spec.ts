import { describe, expect, it, vi } from 'vitest'
import { createChatStreamClient } from './chat-stream'

describe('chat stream client', () => {
  it('uses the completed response when chunk listeners are unavailable', async () => {
    const events: string[] = []
    const startRequest = vi.fn((options: { success(result: { statusCode: number; data: string }): void }) => {
      queueMicrotask(() => options.success({ statusCode: 200, data: 'event: done\ndata: {"type":"done","content":null,"citations":[],"message":null}\n\n' }))
      return { abort: vi.fn() }
    })
    const client = createChatStreamClient({
      baseUrl: 'https://api.example/api/v1',
      startRequest,
      runAuthorized: operation => operation('token').then((result) => {
        if (result.statusCode < 200 || result.statusCode >= 300) throw result.error || new Error('request failed')
      }),
    })
    const handle = client.stream(7, '问题', event => events.push(event.type))
    await handle.completed
    expect(events).toEqual(['done'])
  })

  it('decodes split UTF-8 chunks and does not replay the completed body', async () => {
    const contents: string[] = []
    const body = 'event: delta\ndata: {"type":"delta","content":"回答","citations":[],"message":null}\n\n'
    let chunkListener: ((result: { data: ArrayBuffer }) => void) | undefined
    const startRequest = (options: { success(result: { statusCode: number; data: string }): void }) => {
      queueMicrotask(() => {
        const bytes = new TextEncoder().encode(body)
        const split = bytes.indexOf(0xe7) + 1
        chunkListener?.({ data: bytes.slice(0, split).buffer })
        chunkListener?.({ data: bytes.slice(split).buffer })
        options.success({ statusCode: 200, data: body })
      })
      return {
        abort: vi.fn(),
        onChunkReceived(listener: (result: { data: ArrayBuffer }) => void) {
          chunkListener = listener
        },
      }
    }
    const client = createChatStreamClient({
      baseUrl: 'https://api.example/api/v1',
      startRequest,
      runAuthorized: operation => operation('token').then((result) => {
        if (result.statusCode !== 200) throw result.error || new Error('request failed')
      }),
    })
    await client.stream(7, '问题', event => contents.push(event.content || '')).completed
    expect(contents).toEqual(['回答'])
  })

  it('aborts the active request task', async () => {
    const abort = vi.fn()
    const client = createChatStreamClient({
      baseUrl: 'https://api.example/api/v1',
      startRequest: () => ({ abort, onChunkReceived: vi.fn() }),
      runAuthorized: operation => operation('token').then(result => result.value as void),
    })
    const handle = client.stream(7, '问题', vi.fn())
    await Promise.resolve()
    handle.abort()
    expect(abort).toHaveBeenCalledOnce()
    await expect(handle.completed).rejects.toMatchObject({ name: 'AbortError' })
  })
})
