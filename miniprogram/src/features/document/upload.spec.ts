import { describe, expect, it, vi } from 'vitest'
import { createDocumentUploader } from './upload'

describe('document uploader', () => {
  it('sends field name file and parses the string envelope', async () => {
    const upload = vi.fn().mockResolvedValue({
      statusCode: 200,
      data: JSON.stringify({ code: 'OK', message: 'success', data: { id: 9, originalName: 'a.pdf' }, traceId: 't', timestamp: '2026-07-15T00:00:00Z' }),
    })
    const uploader = createDocumentUploader({
      baseUrl: 'https://api.example/api/v1',
      upload,
      runAuthorized: operation => operation('token').then((result) => {
        if (!result.value) throw new Error('missing value')
        return result.value
      }),
    })
    await expect(uploader.upload(3, '/tmp/a.pdf')).resolves.toEqual({ id: 9, originalName: 'a.pdf' })
    expect(upload).toHaveBeenCalledWith(expect.objectContaining({ name: 'file', header: { Authorization: 'Bearer token' } }))
  })
})
