import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useChatStore } from './store'

const { list } = vi.hoisted(() => ({ list: vi.fn() }))
vi.mock('./api', () => ({ chatApi: { list } }))

const rows = [{
  id: 1,
  title: '会话',
  knowledgeBaseId: 2,
  chatModel: 'QWEN' as const,
  userId: 3,
  createdAt: '2026-07-15T00:00:00Z',
  updatedAt: '2026-07-15T00:00:00Z',
}]

describe('chat store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loads the current user session list', async () => {
    list.mockResolvedValue(rows)
    const store = useChatStore()
    await store.load()
    expect(store.rows).toEqual(rows)
  })
})
