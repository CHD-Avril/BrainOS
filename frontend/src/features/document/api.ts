import http from '@/api/http'

interface ApiEnvelope<T> {
  data: T
}

export type DocumentStatus = 'PARSING' | 'INDEXING' | 'READY' | 'FAILED'

export interface KnowledgeDocument {
  id: number
  knowledgeBaseId: number
  originalName: string
  mimeType: string
  sizeBytes: number
  status: DocumentStatus
  chunkCount: number
  failureReason: string | null
  createdAt: string
  updatedAt: string
  storagePath?: string
  sha256?: string
  uploadedBy?: number
}

function base(knowledgeBaseId: number): string {
  return `/knowledge-bases/${knowledgeBaseId}/documents`
}

export const documentApi = {
  async list(knowledgeBaseId: number): Promise<KnowledgeDocument[]> {
    const response = await http.get<ApiEnvelope<KnowledgeDocument[]>>(base(knowledgeBaseId))
    return response.data.data
  },

  async upload(knowledgeBaseId: number, file: File): Promise<KnowledgeDocument> {
    const form = new FormData()
    form.append('file', file)
    const response = await http.post<ApiEnvelope<KnowledgeDocument>>(base(knowledgeBaseId), form)
    return response.data.data
  },

  async retry(knowledgeBaseId: number, documentId: number): Promise<KnowledgeDocument> {
    const response = await http.post<ApiEnvelope<KnowledgeDocument>>(
      `${base(knowledgeBaseId)}/${documentId}/retry`,
    )
    return response.data.data
  },

  async remove(knowledgeBaseId: number, documentId: number): Promise<void> {
    await http.delete(`${base(knowledgeBaseId)}/${documentId}`)
  },
}
