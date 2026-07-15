import { describe, expect, it, vi } from 'vitest'
import { SseParser } from './sse-parser'

describe('SseParser', () => {
  it('handles a split event boundary and multiple events in one push', () => {
    const listener = vi.fn()
    const parser = new SseParser(listener)
    parser.push('event: delta\ndata: {"type":"delta","content":"回","citations":[],"message":null}\r')
    parser.push('\n\r\nevent: done\ndata: {"type":"done","content":null,"citations":[],"message":null}\n\n')
    expect(listener.mock.calls.map(([event]) => event.type)).toEqual(['delta', 'done'])
  })
})
