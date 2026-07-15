import type { KnowledgeDocument } from './types'

const MAX_DOCUMENT_BYTES = 20 * 1024 * 1024
const SUPPORTED_EXTENSIONS = new Set(['pdf', 'docx', 'txt', 'md', 'markdown'])

interface SelectedDocument {
  name: string
  size: number
}

export function validateDocumentFile(file: SelectedDocument): string | null {
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  if (!SUPPORTED_EXTENSIONS.has(extension)) return '仅支持 PDF、DOCX、TXT、MD、MARKDOWN 文件'
  if (file.size <= 0) return '文档内容不能为空'
  if (file.size > MAX_DOCUMENT_BYTES) return '单个文档不能超过 20MB'
  return null
}

export function hasPendingDocuments(rows: Array<Pick<KnowledgeDocument, 'status'>>): boolean {
  return rows.some(row => row.status === 'PARSING' || row.status === 'INDEXING')
}
