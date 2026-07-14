import http from '@/api/http'

interface ApiEnvelope<T> {
  data: T
}

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

export const knowledgeApi = {
  async list(): Promise<KnowledgeBase[]> {
    const response = await http.get<ApiEnvelope<KnowledgeBase[]>>('/knowledge-bases')
    return response.data.data
  },

  async get(id: number): Promise<KnowledgeBase> {
    const response = await http.get<ApiEnvelope<KnowledgeBase>>(`/knowledge-bases/${id}`)
    return response.data.data
  },

  async create(input: KnowledgeBaseInput): Promise<KnowledgeBase> {
    const response = await http.post<ApiEnvelope<KnowledgeBase>>('/knowledge-bases', input)
    return response.data.data
  },

  async update(id: number, input: KnowledgeBaseInput): Promise<KnowledgeBase> {
    const response = await http.put<ApiEnvelope<KnowledgeBase>>(`/knowledge-bases/${id}`, input)
    return response.data.data
  },

  async remove(id: number): Promise<void> {
    await http.delete(`/knowledge-bases/${id}`)
  },
}
