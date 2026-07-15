import { beforeEach, describe, expect, it, vi } from 'vitest'
import { chatApi, type ChatStreamEvent } from './api'

vi.mock('@/features/auth/session', () => ({
  loadSession: () => ({ accessToken: 'access-token' }),
}))

function streamResponse(chunks: string[], status = 200): Response {
  const encoder = new TextEncoder()
  return new Response(new ReadableStream({
    start(controller) {
      chunks.forEach(chunk => controller.enqueue(encoder.encode(chunk)))
      controller.close()
    },
  }), { status, headers: { 'Content-Type': 'text/event-stream' } })
}

describe('chatApi.stream', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('parses named SSE events split across network chunks', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(streamResponse([
      'event:start\ndata:{"type":"start","content":null,"citations":[],"message":null}\n\n',
      'event:delta\ndata:{"type":"delta","content":"年',
      '假5天","citations":[],"message":null}\r\n\r\n',
      'event:done\ndata:{"type":"done","content":null,"citations":[],"message":null}\n\n',
    ]))
    const events: ChatStreamEvent[] = []

    await chatApi.stream(11, '年假几天？', event => events.push(event))

    expect(events.map(event => event.type)).toEqual(['start', 'delta', 'done'])
    expect(events[1]?.content).toBe('年假5天')
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/chat/sessions/11/messages/stream',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ Authorization: 'Bearer access-token' }),
      }),
    )
  })

  it('rejects non-success responses before parsing a stream', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 404 }))

    await expect(chatApi.stream(11, '越权问题', vi.fn())).rejects.toThrow('404')
  })
})
