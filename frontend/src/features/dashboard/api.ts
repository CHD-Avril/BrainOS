import http from '@/api/http'

interface ApiEnvelope<T> {
  data: T
}

export interface DashboardSummary {
  knowledgeBaseCount: number
  documentCount: number
  chunkCount: number
  questionCount: number
}

export interface DailyCount {
  date: string
  count: number
}

export interface RecentDocument {
  id: number
  knowledgeBaseId: number
  knowledgeBaseName: string
  originalName: string
  status: 'PARSING' | 'INDEXING' | 'READY' | 'FAILED'
  updatedAt: string
}

export const dashboardApi = {
  async summary(): Promise<DashboardSummary> {
    const response = await http.get<ApiEnvelope<DashboardSummary>>('/dashboard/summary')
    return response.data.data
  },

  async trend(): Promise<DailyCount[]> {
    const response = await http.get<ApiEnvelope<DailyCount[]>>('/dashboard/trends', {
      params: { days: 7 },
    })
    return response.data.data
  },

  async recentDocuments(): Promise<RecentDocument[]> {
    const response = await http.get<ApiEnvelope<RecentDocument[]>>('/dashboard/recent-documents', {
      params: { limit: 5 },
    })
    return response.data.data
  },
}
