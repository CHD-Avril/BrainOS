import { apiClient } from '@/api/client'
import type { KnowledgeBase, KnowledgeBaseInput } from './types'

export const knowledgeApi = {
  list: () => apiClient.request<KnowledgeBase[]>({ method: 'GET', path: '/knowledge-bases' }),
  get: (id: number) => apiClient.request<KnowledgeBase>({ method: 'GET', path: `/knowledge-bases/${id}` }),
  create: (input: KnowledgeBaseInput) => apiClient.request<KnowledgeBase>({ method: 'POST', path: '/knowledge-bases', data: input }),
  update: (id: number, input: KnowledgeBaseInput) => apiClient.request<KnowledgeBase>({ method: 'PUT', path: `/knowledge-bases/${id}`, data: input }),
  remove: (id: number) => apiClient.request<void>({ method: 'DELETE', path: `/knowledge-bases/${id}` }),
}
