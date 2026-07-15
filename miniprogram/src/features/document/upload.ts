import { apiClient } from '@/api/client'
import { API_BASE_URL } from '@/config/env'
import { ApiError } from '@/api/errors'
import type { ApiEnvelope } from '@/types/api'
import type { KnowledgeDocument } from './types'

interface UploadOptions {
  url: string
  filePath: string
  name: string
  header: Record<string, string>
}

interface UploadResponse {
  statusCode: number
  data: string
}

interface AuthorizedResult<T> {
  statusCode: number
  value?: T
  error?: ApiError
}

interface DocumentUploaderDependencies {
  baseUrl: string
  upload(options: UploadOptions): Promise<UploadResponse>
  runAuthorized(operation: (accessToken: string) => Promise<AuthorizedResult<KnowledgeDocument>>): Promise<KnowledgeDocument>
}

function parseUploadResponse(response: UploadResponse): AuthorizedResult<KnowledgeDocument> {
  let body: Partial<ApiEnvelope<KnowledgeDocument>> | null = null
  try {
    body = JSON.parse(response.data) as Partial<ApiEnvelope<KnowledgeDocument>>
  }
  catch {
    return {
      statusCode: response.statusCode,
      error: new ApiError('服务端响应格式错误', response.statusCode, 'INVALID_RESPONSE'),
    }
  }
  if (!body || typeof body !== 'object' || typeof body.code !== 'string') {
    return {
      statusCode: response.statusCode,
      error: new ApiError('服务端响应格式错误', response.statusCode, 'INVALID_RESPONSE'),
    }
  }
  if (response.statusCode < 200 || response.statusCode >= 300 || body.code !== 'OK') {
    return {
      statusCode: response.statusCode,
      error: new ApiError(body.message || '上传失败', response.statusCode, body.code, body.traceId || ''),
    }
  }
  return { statusCode: response.statusCode, value: body.data as KnowledgeDocument }
}

export function createDocumentUploader(deps: DocumentUploaderDependencies) {
  return {
    upload(knowledgeBaseId: number, filePath: string): Promise<KnowledgeDocument> {
      return deps.runAuthorized(async accessToken => parseUploadResponse(await deps.upload({
        url: `${deps.baseUrl}/knowledge-bases/${knowledgeBaseId}/documents`,
        filePath,
        name: 'file',
        header: { Authorization: `Bearer ${accessToken}` },
      })))
    },
  }
}

export const documentUploader = createDocumentUploader({
  baseUrl: API_BASE_URL,
  upload(options) {
    return new Promise((resolve, reject) => {
      uni.uploadFile({
        ...options,
        success: result => resolve({ statusCode: result.statusCode, data: result.data }),
        fail: error => reject(new ApiError(error.errMsg || '文件上传失败')),
      })
    })
  },
  runAuthorized: operation => apiClient.runAuthorized(operation),
})
