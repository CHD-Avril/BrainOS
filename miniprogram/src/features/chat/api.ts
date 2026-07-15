import { apiClient } from '@/api/client'
import type { ChatModel, ChatSession, ChatSessionDetail } from './types'

export const chatApi = {
  create: (knowledgeBaseId: number, chatModel: ChatModel) => apiClient.request<ChatSession>({
    method: 'POST',
    path: '/chat/sessions',
    data: { knowledgeBaseId, chatModel },
  }),
  list: () => apiClient.request<ChatSession[]>({ method: 'GET', path: '/chat/sessions' }),
  get: (id: number) => apiClient.request<ChatSessionDetail>({ method: 'GET', path: `/chat/sessions/${id}` }),
  rename: (id: number, title: string) => apiClient.request<ChatSession>({
    method: 'PUT',
    path: `/chat/sessions/${id}`,
    data: { title },
  }),
  remove: (id: number) => apiClient.request<void>({ method: 'DELETE', path: `/chat/sessions/${id}` }),
}
