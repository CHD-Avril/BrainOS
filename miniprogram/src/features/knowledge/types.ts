export interface KnowledgeBase {
  id: number
  name: string
  description: string | null
  createdBy: number
  documentCount: number
  readyDocumentCount: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeBaseInput {
  name: string
  description: string | null
}
