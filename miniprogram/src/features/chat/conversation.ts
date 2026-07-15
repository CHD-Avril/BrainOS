import { reactive } from 'vue'
import type { StreamHandle } from '@/api/chat-stream'
import type { ChatSession, ChatSessionDetail, ChatStreamEvent, CitationCandidate } from './types'

export interface ConversationMessage {
  key: string
  id?: number
  role: 'USER' | 'ASSISTANT'
  content: string
  citations: CitationCandidate[]
  streaming: boolean
  stopped: boolean
  error: boolean
  retryQuestion?: string
}

interface ConversationDependencies {
  get(sessionId: number): Promise<ChatSessionDetail>
  stream(sessionId: number, question: string, onEvent: (event: ChatStreamEvent) => void): StreamHandle
}

export interface ConversationController {
  session: ChatSession | null
  messages: ConversationMessage[]
  loading: boolean
  streaming: boolean
  error: string
  load(sessionId: number): Promise<void>
  send(sessionId: number, question: string): Promise<void>
  stop(): void
  retry(message: ConversationMessage): Promise<void>
}

let localSequence = 0

export function createConversationController(deps: ConversationDependencies): ConversationController {
  let currentHandle: StreamHandle | undefined

  const controller: ConversationController = reactive({
    session: null,
    messages: [],
    loading: false,
    streaming: false,
    error: '',

    async load(sessionId: number) {
      controller.loading = true
      controller.error = ''
      try {
        const detail = await deps.get(sessionId)
        controller.session = { ...detail.session }
        controller.messages.splice(0, controller.messages.length, ...detail.messages.map(message => ({
          key: `server-${message.id}`,
          id: message.id,
          role: message.role,
          content: message.content,
          citations: message.citations.map(citation => ({ ...citation })),
          streaming: false,
          stopped: false,
          error: false,
        })))
      }
      catch (error) {
        controller.error = error instanceof Error ? error.message : '会话加载失败'
        throw error
      }
      finally {
        controller.loading = false
      }
    },

    async send(sessionId: number, questionValue: string) {
      const question = questionValue.trim()
      if (controller.streaming) return
      if (!question) {
        controller.error = '请输入问题'
        return
      }
      if (question.length > 1000) {
        controller.error = '问题不能超过 1000 个字符'
        return
      }

      controller.error = ''
      const sequence = ++localSequence
      controller.messages.push({
        key: `local-user-${sequence}`,
        role: 'USER',
        content: question,
        citations: [],
        streaming: false,
        stopped: false,
        error: false,
      })
      const assistant: ConversationMessage = reactive({
        key: `local-assistant-${sequence}`,
        role: 'ASSISTANT',
        content: '',
        citations: [],
        streaming: true,
        stopped: false,
        error: false,
        retryQuestion: question,
      })
      controller.messages.push(assistant)
      controller.streaming = true

      const onEvent = (event: ChatStreamEvent) => {
        if (event.type === 'delta' && event.content) assistant.content += event.content
        else if (event.type === 'citations') assistant.citations.push(...event.citations.map(citation => ({ ...citation })))
        else if (event.type === 'error') {
          assistant.error = true
          controller.error = event.message || '回答生成失败'
        }
        else if (event.type === 'done') assistant.streaming = false
      }

      try {
        currentHandle = deps.stream(sessionId, question, onEvent)
        await currentHandle.completed
      }
      catch (error) {
        if (error instanceof Error && error.name === 'AbortError') assistant.stopped = true
        else {
          assistant.error = true
          controller.error = '连接中断，请重试本次提问'
        }
      }
      finally {
        assistant.streaming = false
        controller.streaming = false
        currentHandle = undefined
      }
    },

    stop() {
      const handle = currentHandle
      if (!handle) return
      currentHandle = undefined
      handle.abort()
    },

    async retry(message: ConversationMessage) {
      if (message.retryQuestion) await controller.send(controller.session?.id || 0, message.retryQuestion)
    },
  })

  return controller
}
