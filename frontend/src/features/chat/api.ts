import http from '@/api/http'
import { loadSession } from '@/features/auth/session'

interface ApiResponse<T> {
  data: T
}

export type ChatModel = 'QWEN' | 'DEEPSEEK'

export interface CitationCandidate {
  knowledgeBaseId: number
  documentId: number
  chunkId: string
  fileName: string
  pageNumber: number | null
  chunkIndex: number
  snippet: string
  score: number
}

export interface ChatSession {
  id: number
  title: string
  knowledgeBaseId: number
  chatModel: ChatModel
  userId: number
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: number
  sessionId: number
  role: 'USER' | 'ASSISTANT'
  content: string
  citations: CitationCandidate[]
  createdAt: string
}

export interface ChatSessionDetail {
  session: ChatSession
  messages: ChatMessage[]
}

export interface ChatStreamEvent {
  type: 'start' | 'delta' | 'citations' | 'done' | 'error'
  content: string | null
  citations: CitationCandidate[]
  message: string | null
}

export const chatApi = {
  async create(knowledgeBaseId: number, chatModel: ChatModel): Promise<ChatSession> {
    const response = await http.post<ApiResponse<ChatSession>>('/chat/sessions', {
      knowledgeBaseId,
      chatModel,
    })
    return response.data.data
  },

  async list(): Promise<ChatSession[]> {
    const response = await http.get<ApiResponse<ChatSession[]>>('/chat/sessions')
    return response.data.data
  },

  async get(id: number): Promise<ChatSessionDetail> {
    const response = await http.get<ApiResponse<ChatSessionDetail>>(`/chat/sessions/${id}`)
    return response.data.data
  },

  async rename(id: number, title: string): Promise<ChatSession> {
    const response = await http.put<ApiResponse<ChatSession>>(`/chat/sessions/${id}`, { title })
    return response.data.data
  },

  async remove(id: number): Promise<void> {
    await http.delete(`/chat/sessions/${id}`)
  },

  async stream(
    id: number,
    question: string,
    onEvent: (event: ChatStreamEvent) => void,
    signal?: AbortSignal,
  ): Promise<void> {
    const token = loadSession()?.accessToken
    const response = await fetch(`/api/v1/chat/sessions/${id}/messages/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ question }),
      signal,
    })
    if (!response.ok) throw new Error(`chat stream failed with ${response.status}`)
    if (!response.body) throw new Error('chat stream is unavailable')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      const blocks = buffer.split(/\r?\n\r?\n/)
      buffer = blocks.pop() ?? ''
      for (const block of blocks) dispatchSseBlock(block, onEvent)
      if (done) break
    }
    if (buffer.trim()) dispatchSseBlock(buffer, onEvent)
  },
}

function dispatchSseBlock(
  block: string,
  onEvent: (event: ChatStreamEvent) => void,
): void {
  let eventName = ''
  const data: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) eventName = line.slice(6).trim()
    if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
  }
  if (!data.length) return
  const event = JSON.parse(data.join('\n')) as ChatStreamEvent
  onEvent({ ...event, type: (eventName || event.type) as ChatStreamEvent['type'] })
}
