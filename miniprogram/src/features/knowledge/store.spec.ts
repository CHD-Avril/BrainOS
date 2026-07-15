import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useKnowledgeStore } from './store'

const { list } = vi.hoisted(() => ({ list: vi.fn() }))
vi.mock('./api', () => ({ knowledgeApi: { list } }))

const original = [{
  id: 1,
  name: '制度',
  description: null,
  createdBy: 1,
  documentCount: 1,
  readyDocumentCount: 1,
  createdAt: '2026-07-15T00:00:00Z',
  updatedAt: '2026-07-15T00:00:00Z',
}]

describe('knowledge store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('keeps existing rows when refresh fails', async () => {
    list.mockResolvedValueOnce(original).mockRejectedValueOnce(new Error('offline'))
    const store = useKnowledgeStore()
    await store.load()
    await expect(store.load()).rejects.toThrow('offline')
    expect(store.rows).toEqual(original)
    expect(store.error).toBe('offline')
  })
})
