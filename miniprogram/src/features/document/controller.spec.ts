import { describe, expect, it } from 'vitest'
import { hasPendingDocuments, validateDocumentFile } from './controller'

describe('document controller', () => {
  it('accepts supported case-insensitive extensions at exactly 20 MiB', () => {
    expect(validateDocumentFile({ name: '制度.PDF', size: 20 * 1024 * 1024 })).toBeNull()
  })

  it('rejects unsupported and oversized files', () => {
    expect(validateDocumentFile({ name: 'sheet.xlsx', size: 1 })).toContain('PDF')
    expect(validateDocumentFile({ name: 'large.md', size: 20 * 1024 * 1024 + 1 })).toBe('单个文档不能超过 20MB')
  })

  it('polls only parsing or indexing rows', () => {
    expect(hasPendingDocuments([{ status: 'READY' }, { status: 'INDEXING' }])).toBe(true)
    expect(hasPendingDocuments([{ status: 'READY' }, { status: 'FAILED' }])).toBe(false)
  })
})
