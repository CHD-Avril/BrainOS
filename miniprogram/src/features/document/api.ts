import { apiClient } from '@/api/client'
import type { KnowledgeDocument } from './types'

function base(knowledgeBaseId: number): string {
  return `/knowledge-bases/${knowledgeBaseId}/documents`
}

export const documentApi = {
  list: (knowledgeBaseId: number) => apiClient.request<KnowledgeDocument[]>({ method: 'GET', path: base(knowledgeBaseId) }),
  retry: (knowledgeBaseId: number, documentId: number) => apiClient.request<KnowledgeDocument>({
    method: 'POST',
    path: `${base(knowledgeBaseId)}/${documentId}/retry`,
  }),
  remove: (knowledgeBaseId: number, documentId: number) => apiClient.request<void>({
    method: 'DELETE',
    path: `${base(knowledgeBaseId)}/${documentId}`,
  }),
}
