import ElementPlus, { ElSelect } from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ChatView from './ChatView.vue'
import { chatApi, type ChatStreamEvent } from './api'
import { knowledgeApi } from '@/features/knowledge/api'

vi.mock('./api', () => ({
  chatApi: {
    create: vi.fn(),
    list: vi.fn(),
    get: vi.fn(),
    rename: vi.fn(),
    remove: vi.fn(),
    stream: vi.fn(),
  },
}))

vi.mock('@/features/knowledge/api', () => ({
  knowledgeApi: { list: vi.fn() },
}))

const knowledge = {
  id: 7,
  name: '员工制度',
  description: '制度资料',
  createdBy: 1,
  documentCount: 1,
  readyDocumentCount: 1,
  createdAt: '2026-07-14T00:00:00Z',
  updatedAt: '2026-07-14T00:00:00Z',
}

const session = {
  id: 11,
  title: '年假制度',
  knowledgeBaseId: 7,
  chatModel: 'QWEN' as const,
  userId: 9,
  createdAt: '2026-07-14T00:00:00Z',
  updatedAt: '2026-07-14T00:00:00Z',
}

describe('ChatView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(knowledgeApi.list).mockResolvedValue([knowledge])
    vi.mocked(chatApi.list).mockResolvedValue([session])
    vi.mocked(chatApi.get).mockResolvedValue({ session, messages: [] })
  })

  it('streams an answer and exposes its cited source', async () => {
    const citation = {
      knowledgeBaseId: 7,
      documentId: 21,
      chunkId: 'chunk-1',
      fileName: '员工手册.pdf',
      pageNumber: 3,
      chunkIndex: 0,
      snippet: '正式员工每年享有5天年假。',
      score: 0.91,
    }
    vi.mocked(chatApi.stream).mockImplementation(async (_id, _question, onEvent) => {
      const events: ChatStreamEvent[] = [
        { type: 'start', content: null, citations: [], message: null },
        { type: 'delta', content: '正式员工每年享有', citations: [], message: null },
        { type: 'delta', content: '**5天年假**。[来源1]', citations: [], message: null },
        { type: 'citations', content: null, citations: [citation], message: null },
        { type: 'done', content: null, citations: [], message: null },
      ]
      events.forEach(onEvent)
    })
    const wrapper = mount(ChatView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    await wrapper.get('[data-test="chat-input"]').setValue('年假有几天？')
    await wrapper.get('[data-test="send-question"]').trigger('click')
    await flushPromises()

    expect(chatApi.stream).toHaveBeenCalledWith(11, '年假有几天？', expect.any(Function), expect.any(AbortSignal))
    expect(wrapper.text()).toContain('正式员工每年享有')
    expect(wrapper.get('.assistant-message strong').text()).toBe('5天年假')
    expect(wrapper.text()).toContain('员工手册.pdf')
    expect(wrapper.text()).toContain('来源 1')
    expect(wrapper.text()).toContain('91%')
  })

  it('creates a Qwen session before the first question when history is empty', async () => {
    vi.mocked(chatApi.list)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([session])
    vi.mocked(chatApi.create).mockResolvedValue(session)
    vi.mocked(chatApi.stream).mockResolvedValue()
    const wrapper = mount(ChatView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    await wrapper.get('[data-test="chat-input"]').setValue('试用期规定是什么？')
    await wrapper.get('[data-test="send-question"]').trigger('click')
    await flushPromises()

    expect(chatApi.create).toHaveBeenCalledWith(7, 'QWEN')
    expect(chatApi.stream).toHaveBeenCalledWith(11, '试用期规定是什么？', expect.any(Function), expect.any(AbortSignal))
  })

  it('creates a ChatGPT session when the user selects ChatGPT', async () => {
    const chatGptSession = { ...session, id: 12, chatModel: 'CHATGPT' as const }
    vi.mocked(chatApi.list)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([chatGptSession])
    vi.mocked(chatApi.create).mockResolvedValue(chatGptSession)
    vi.mocked(chatApi.stream).mockResolvedValue()
    const wrapper = mount(ChatView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    const selects = wrapper.findAllComponents(ElSelect)
    expect(selects).toHaveLength(2)
    selects[1]!.vm.$emit('update:modelValue', 'CHATGPT')
    await flushPromises()
    await wrapper.get('[data-test="chat-input"]').setValue('年假怎么申请？')
    await wrapper.get('[data-test="send-question"]').trigger('click')
    await flushPromises()

    expect(chatApi.create).toHaveBeenCalledWith(7, 'CHATGPT')
    expect(chatApi.stream).toHaveBeenCalledWith(12, '年假怎么申请？', expect.any(Function), expect.any(AbortSignal))
  })

  it('stops an active stream and lets the user retry the preserved question', async () => {
    vi.mocked(chatApi.stream)
      .mockImplementationOnce(async (_id, _question, _onEvent, signal) => {
        await new Promise<void>((_resolve, reject) => {
          signal?.addEventListener('abort', () => reject(new DOMException('stopped', 'AbortError')))
        })
      })
      .mockImplementationOnce(async (_id, _question, onEvent) => {
        onEvent({ type: 'delta', content: '重试成功', citations: [], message: null })
        onEvent({ type: 'done', content: null, citations: [], message: null })
      })
    const wrapper = mount(ChatView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    await wrapper.get('[data-test="chat-input"]').setValue('年假有几天？')
    await wrapper.get('[data-test="send-question"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-test="stop-generation"]').text()).toContain('停止生成')

    await wrapper.get('[data-test="stop-generation"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('生成已停止')
    await wrapper.get('[data-test="retry-answer"]').trigger('click')
    await flushPromises()

    expect(chatApi.stream).toHaveBeenCalledTimes(2)
    expect(vi.mocked(chatApi.stream).mock.calls[1]?.[1]).toBe('年假有几天？')
    expect(wrapper.text()).toContain('重试成功')
  })

  it('keeps the question and offers retry when the provider returns an error event', async () => {
    vi.mocked(chatApi.stream).mockImplementation(async (_id, _question, onEvent) => {
      onEvent({
        type: 'error',
        content: null,
        citations: [],
        message: '回答生成失败，请稍后重试',
      })
    })
    const wrapper = mount(ChatView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    await wrapper.get('[data-test="chat-input"]').setValue('薪资什么时候发？')
    await wrapper.get('[data-test="send-question"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('薪资什么时候发？')
    expect(wrapper.text()).toContain('回答生成失败')
    expect(wrapper.get('[data-test="retry-answer"]').text()).toContain('重试')
  })

  it('prevents duplicate session creation while the first create is pending', async () => {
    let resolveCreate!: (value: typeof session) => void
    vi.mocked(chatApi.list).mockResolvedValue([])
    vi.mocked(chatApi.create).mockImplementation(() => new Promise(resolve => {
      resolveCreate = resolve
    }))
    const wrapper = mount(ChatView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    await wrapper.get('[data-test="chat-input"]').setValue('试用期规定？')
    const send = wrapper.get('[data-test="send-question"]')
    void send.trigger('click')
    await flushPromises()
    await send.trigger('click')

    expect(chatApi.create).toHaveBeenCalledTimes(1)
    resolveCreate(session)
    await flushPromises()
  })

  it('ignores stale history when users switch sessions quickly', async () => {
    const secondSession = { ...session, id: 12, title: '试用期制度' }
    let resolveFirst!: (value: Awaited<ReturnType<typeof chatApi.get>>) => void
    vi.mocked(chatApi.list).mockResolvedValue([session, secondSession])
    vi.mocked(chatApi.get)
      .mockImplementationOnce(() => new Promise(resolve => {
        resolveFirst = resolve
      }))
      .mockResolvedValueOnce({
        session: secondSession,
        messages: [{
          id: 20,
          sessionId: 12,
          role: 'ASSISTANT',
          content: '这是第二个会话',
          citations: [],
          createdAt: '2026-07-14T02:00:00Z',
        }],
      })
    const wrapper = mount(ChatView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    await wrapper.findAll('.session-item')[1]!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('这是第二个会话')

    resolveFirst({
      session,
      messages: [{
        id: 10,
        sessionId: 11,
        role: 'ASSISTANT',
        content: '迟到的第一个会话',
        citations: [],
        createdAt: '2026-07-14T01:00:00Z',
      }],
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('迟到的第一个会话')
    expect(wrapper.text()).toContain('这是第二个会话')
  })
})
