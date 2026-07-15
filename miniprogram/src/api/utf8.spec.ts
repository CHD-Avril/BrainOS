import { describe, expect, it } from 'vitest'
import { Utf8StreamDecoder } from './utf8'

describe('Utf8StreamDecoder', () => {
  it('preserves a Chinese code point split between chunks', () => {
    const bytes = new TextEncoder().encode('回答')
    const decoder = new Utf8StreamDecoder()
    expect(decoder.push(bytes.slice(0, 4))).toBe('回')
    expect(decoder.push(bytes.slice(4))).toBe('答')
    expect(decoder.finish()).toBe('')
  })
})
