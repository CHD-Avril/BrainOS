import http from '@/api/http'

interface ApiEnvelope<T> {
  data: T
}

export interface PagedResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export type UserRole = 'ADMIN' | 'USER'
export type UserStatus = 'ENABLED' | 'DISABLED'

export interface ManagedUser {
  id: number
  username: string
  displayName: string
  role: UserRole
  status: UserStatus
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateUserInput {
  username: string
  displayName: string
  password: string
  role: UserRole
}

export interface UpdateUserInput {
  displayName: string
  password?: string
  role: UserRole
}

export const userAdminApi = {
  async list(page: number, size: number): Promise<PagedResult<ManagedUser>> {
    const response = await http.get<ApiEnvelope<PagedResult<ManagedUser>>>('/admin/users', {
      params: { page, size },
    })
    return response.data.data
  },

  async create(input: CreateUserInput): Promise<ManagedUser> {
    const response = await http.post<ApiEnvelope<ManagedUser>>('/admin/users', input)
    return response.data.data
  },

  async update(id: number, input: UpdateUserInput): Promise<ManagedUser> {
    const response = await http.put<ApiEnvelope<ManagedUser>>(`/admin/users/${id}`, input)
    return response.data.data
  },

  async changeStatus(id: number, status: UserStatus): Promise<ManagedUser> {
    const response = await http.patch<ApiEnvelope<ManagedUser>>(`/admin/users/${id}/status`, {
      status,
    })
    return response.data.data
  },
}
