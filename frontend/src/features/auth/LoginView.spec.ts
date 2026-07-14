import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginView from './LoginView.vue'
import { authApi } from './api'
import { createBrainOsRouter } from '@/router'

vi.mock('./api', () => ({
  authApi: {
    login: vi.fn(),
    refresh: vi.fn(),
    logout: vi.fn(),
  },
}))

async function renderLogin() {
  const pinia = createPinia()
  const router = createBrainOsRouter(createMemoryHistory(), pinia)
  await router.push('/login')
  await router.isReady()

  const wrapper = mount(LoginView, {
    attachTo: document.body,
    global: { plugins: [pinia, router, ElementPlus] },
  })

  return { wrapper, router }
}

describe('LoginView', () => {
  beforeEach(() => {
    vi.mocked(authApi.login).mockReset()
  })

  it('shows inline Chinese errors for an empty form without calling the API', async () => {
    const { wrapper } = await renderLogin()

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('请输入用户名')
      expect(wrapper.text()).toContain('请输入密码')
    })
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('submits credentials and routes to the dashboard', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN' },
    })
    const { wrapper, router } = await renderLogin()

    await wrapper.get('[aria-label="用户名"]').setValue('admin')
    await wrapper.get('[aria-label="密码"]').setValue('BrainOS@123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(authApi.login).toHaveBeenCalledWith('admin', 'BrainOS@123')
    expect(router.currentRoute.value.name).toBe('dashboard')
  })

  it('shows only the generic authentication error when the API rejects', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error('internal database detail'))
    const { wrapper } = await renderLogin()

    await wrapper.get('[aria-label="用户名"]').setValue('admin')
    await wrapper.get('[aria-label="密码"]').setValue('wrong')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('用户名或密码错误，请重试')
    expect(wrapper.text()).not.toContain('internal database detail')
  })

  it('ignores duplicate submissions while login is pending', async () => {
    let resolveLogin!: (value: Awaited<ReturnType<typeof authApi.login>>) => void
    vi.mocked(authApi.login).mockImplementation(
      () => new Promise((resolve) => { resolveLogin = resolve }),
    )
    const { wrapper } = await renderLogin()
    await wrapper.get('[aria-label="用户名"]').setValue('admin')
    await wrapper.get('[aria-label="密码"]').setValue('BrainOS@123')

    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')

    expect(authApi.login).toHaveBeenCalledTimes(1)
    expect(wrapper.get('button[type="submit"]').attributes()).toHaveProperty('disabled')

    resolveLogin({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN' },
    })
    await flushPromises()
  })
})
