import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DocumentListView from './DocumentListView.vue'
import { documentApi } from './api'
import { knowledgeApi } from '@/features/knowledge/api'

vi.mock('./api', () => ({
  documentApi: {
    list: vi.fn(),
    upload: vi.fn(),
    retry: vi.fn(),
    remove: vi.fn(),
  },
}))

vi.mock('@/features/knowledge/api', () => ({
  knowledgeApi: {
    get: vi.fn(),
  },
}))

const processing = {
  id: 44,
  knowledgeBaseId: 7,
  originalName: '员工手册.md',
  mimeType: 'text/markdown',
  sizeBytes: 2048,
  status: 'INDEXING' as const,
  chunkCount: 0,
  failureReason: null,
  createdAt: '2026-07-14T00:00:00Z',
  updatedAt: '2026-07-14T00:00:00Z',
}

async function renderDocuments() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{
      path: '/knowledge-bases/:id/documents',
      component: DocumentListView,
    }],
  })
  await router.push('/knowledge-bases/7/documents')
  await router.isReady()
  const wrapper = mount(DocumentListView, {
    attachTo: document.body,
    global: { plugins: [router, ElementPlus] },
  })
  await flushPromises()
  return wrapper
}

describe('DocumentListView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(knowledgeApi.get).mockResolvedValue({
      id: 7,
      name: '员工制度',
      description: '公司制度与员工手册',
      createdBy: 1,
      documentCount: 1,
      readyDocumentCount: 0,
      createdAt: '2026-07-14T00:00:00Z',
      updatedAt: '2026-07-14T00:00:00Z',
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('polls processing documents and exposes a readable retry state', async () => {
    vi.mocked(documentApi.list)
      .mockResolvedValueOnce([processing])
      .mockResolvedValueOnce([{
        ...processing,
        status: 'FAILED',
        failureReason: '未提取到可用文本',
      }])

    const wrapper = await renderDocuments()

    expect(wrapper.text()).toContain('员工制度')
    expect(wrapper.get('[data-test="document-status"]').text()).toContain('索引中')
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()

    expect(documentApi.list).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-test="failure-reason"]').text()).toContain('未提取到可用文本')
    expect(wrapper.get('[data-test="retry"]').isVisible()).toBe(true)
  })

  it('stops polling once every document is ready', async () => {
    vi.mocked(documentApi.list).mockResolvedValue([{
      ...processing,
      status: 'READY',
      chunkCount: 6,
    }])

    await renderDocuments()
    await vi.advanceTimersByTimeAsync(4000)

    expect(documentApi.list).toHaveBeenCalledTimes(1)
  })
})
