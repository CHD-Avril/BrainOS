import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AuditLogView from './AuditLogView.vue'
import { auditApi } from './api'

vi.mock('./api', () => ({
  auditApi: { list: vi.fn() },
}))

describe('AuditLogView', () => {
  beforeEach(() => {
    vi.mocked(auditApi.list).mockResolvedValue({
      items: [{
        id: 18,
        userId: 1,
        username: 'admin',
        action: 'DOCUMENT_UPLOAD',
        targetType: 'DOCUMENT',
        targetId: '44',
        result: 'SUCCESS',
        summary: '上传文档',
        createdAt: '2026-07-14T08:00:00Z',
      }],
      total: 1,
      page: 1,
      size: 20,
    })
  })

  it('renders safe audit details and available filters', async () => {
    const wrapper = mount(AuditLogView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.get('h2').text()).toBe('操作日志')
    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).toContain('上传文档')
    expect(wrapper.text()).toContain('文档 #44')
    expect(wrapper.text()).toContain('成功')
    expect(wrapper.get('[aria-label="操作人 ID"]')).toBeTruthy()
  })
})
