import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { describe, expect, it } from 'vitest'
import { useAuthStore } from '@/features/auth/store'
import { createBrainOsRouter } from '@/router'
import AppShell from './AppShell.vue'

async function renderShell(role: 'ADMIN' | 'USER') {
  const pinia = createPinia()
  useAuthStore(pinia).session = {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    user: { id: 1, username: role === 'ADMIN' ? 'admin' : 'user', displayName: '用户', role },
  }
  const router = createBrainOsRouter(createMemoryHistory(), pinia)
  await router.push('/dashboard')
  await router.isReady()
  const wrapper = mount(AppShell, {
    global: {
      plugins: [pinia, router, ElementPlus],
      stubs: { RouterView: true },
    },
  })
  return wrapper
}

describe('AppShell navigation', () => {
  it('shows the approved navigation in order, including admin entries for administrators', async () => {
    const wrapper = await renderShell('ADMIN')

    expect(wrapper.findAll('[aria-label="主导航"] .el-menu-item').map((item) => item.text())).toEqual([
      '工作台', '知识库', 'AI 问答', '用户管理', '操作日志',
    ])
  })

  it('does not expose administrator navigation to a normal user', async () => {
    const wrapper = await renderShell('USER')

    expect(wrapper.findAll('[aria-label="主导航"] .el-menu-item').map((item) => item.text())).toEqual([
      '工作台', '知识库', 'AI 问答',
    ])
    expect(wrapper.text()).not.toContain('用户管理')
    expect(wrapper.text()).not.toContain('操作日志')
  })
})
