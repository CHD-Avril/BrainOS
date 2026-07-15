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

export type ChatStreamEventType = 'start' | 'delta' | 'citations' | 'done' | 'error'

export interface ChatStreamEvent {
  type: ChatStreamEventType
  content: string | null
  citations: CitationCandidate[]
  message: string | null
}
