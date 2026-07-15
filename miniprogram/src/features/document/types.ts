export type DocumentStatus = 'PARSING' | 'INDEXING' | 'READY' | 'FAILED'

export interface KnowledgeDocument {
  id: number
  knowledgeBaseId: number
  originalName: string
  storagePath: string
  mimeType: string
  sizeBytes: number
  sha256: string
  status: DocumentStatus
  chunkCount: number
  failureReason: string | null
  uploadedBy: number
  createdAt: string
  updatedAt: string
}
