import type { ChatStreamEvent, ChatStreamEventType } from '@/features/chat/types'
import { ApiError } from './errors'

export class SseParser {
  private buffer = ''

  constructor(private readonly listener: (event: ChatStreamEvent) => void) {}

  push(text: string): void {
    this.buffer += text
    let boundary = this.buffer.match(/\r?\n\r?\n/)
    while (boundary?.index !== undefined) {
      const block = this.buffer.slice(0, boundary.index)
      this.buffer = this.buffer.slice(boundary.index + boundary[0].length)
      this.dispatch(block)
      boundary = this.buffer.match(/\r?\n\r?\n/)
    }
  }

  finish(): void {
    const block = this.buffer
    this.buffer = ''
    if (block.trim()) this.dispatch(block)
  }

  private dispatch(block: string): void {
    let eventName = ''
    const data: string[] = []
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
    }
    if (data.length === 0) return
    try {
      const event = JSON.parse(data.join('\n')) as ChatStreamEvent
      this.listener({ ...event, type: (eventName || event.type) as ChatStreamEventType })
    }
    catch {
      throw new ApiError('流式响应格式错误', 200, 'INVALID_SSE')
    }
  }
}
