import { describe, expect, it, vi } from 'vitest'
import type { ChatStreamEvent } from './types'
import { createConversationController } from './conversation'

describe('conversation controller', () => {
  it('appends deltas and citations to one assistant message', async () => {
    const stream = vi.fn((_id: number, _question: string, onEvent: (event: ChatStreamEvent) => void) => {
      const completed = Promise.resolve().then(() => {
        onEvent({ type: 'delta', content: '答案', citations: [], message: null })
        onEvent({ type: 'citations', content: null, citations: [{ knowledgeBaseId: 1, documentId: 2, chunkId: '2:0', fileName: '制度.pdf', pageNumber: 1, chunkIndex: 0, snippet: '依据', score: 0.9 }], message: null })
        onEvent({ type: 'done', content: null, citations: [], message: null })
      })
      return { completed, abort: vi.fn() }
    })
    const controller = createConversationController({ get: vi.fn(), stream })
    await controller.send(9, '问题')
    expect(controller.messages.at(-1)).toMatchObject({ role: 'ASSISTANT', content: '答案', citations: [{ fileName: '制度.pdf' }], streaming: false })
  })

  it('marks an aborted partial answer as stopped and retains its text', async () => {
    let reject!: (error: Error) => void
    const completed = new Promise<void>((_, fail) => { reject = fail })
    const controller = createConversationController({
      get: vi.fn(),
      stream: (_id: number, _question: string, onEvent: (event: ChatStreamEvent) => void) => {
        onEvent({ type: 'delta', content: '部分', citations: [], message: null })
        return {
          completed,
          abort: () => {
            const error = new Error('aborted')
            error.name = 'AbortError'
            reject(error)
          },
        }
      },
    })
    const sending = controller.send(9, '问题')
    controller.stop()
    await sending
    expect(controller.messages.at(-1)).toMatchObject({ content: '部分', stopped: true, streaming: false })
  })
})
