import http from '@/api/http'
import type { PagedResult } from '@/features/admin/users/api'

interface ApiEnvelope<T> {
  data: T
}

export interface AuditLog {
  id: number
  userId: number | null
  username: string | null
  action: string
  targetType: string
  targetId: string | null
  result: 'SUCCESS' | 'FAILURE'
  summary: string | null
  createdAt: string
}

export interface AuditQuery {
  userId?: number
  action?: string
  from?: string
  to?: string
  page: number
  size: number
}

export const auditApi = {
  async list(query: AuditQuery): Promise<PagedResult<AuditLog>> {
    const response = await http.get<ApiEnvelope<PagedResult<AuditLog>>>('/admin/audit-logs', {
      params: query,
    })
    return response.data.data
  },
}
