import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UserAdminView from './UserAdminView.vue'
import userAdminViewSource from './UserAdminView.vue?raw'
import { userAdminApi } from './api'

vi.mock('./api', () => ({
  userAdminApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    changeStatus: vi.fn(),
  },
}))

describe('UserAdminView', () => {
  beforeEach(() => {
    vi.mocked(userAdminApi.list).mockResolvedValue({
      items: [{
        id: 3,
        username: 'knowledge.owner',
        displayName: '知识运营',
        role: 'ADMIN',
        status: 'ENABLED',
        lastLoginAt: '2026-07-14T08:00:00Z',
        createdAt: '2026-07-01T08:00:00Z',
        updatedAt: '2026-07-14T08:00:00Z',
      }],
      total: 1,
      page: 1,
      size: 10,
    })
  })

  it('uses one explicit alignment rule for every non-user table column', () => {
    const centeredColumns = userAdminViewSource.match(/<el-table-column[^>]*align="center"[^>]*>/g) ?? []

    expect(centeredColumns).toHaveLength(5)
    expect(userAdminViewSource).toContain('class="time-cell"')
    expect(userAdminViewSource).toContain('class="user-actions"')
  })

  it('shows user status and opens the create form', async () => {
    const wrapper = mount(UserAdminView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.get('h2').text()).toBe('用户管理')
    expect(wrapper.text()).toContain('知识运营')
    expect(wrapper.text()).toContain('管理员')
    expect(wrapper.text()).toContain('已启用')

    await wrapper.get('[data-test="create-user"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('初始密码')
    expect(document.body.textContent).toContain('普通用户')
  })
})
