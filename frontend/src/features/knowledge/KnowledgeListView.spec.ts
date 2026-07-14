import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import KnowledgeListView from './KnowledgeListView.vue'
import { knowledgeApi } from './api'

vi.mock('./api', () => ({
  knowledgeApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}))

describe('KnowledgeListView', () => {
  beforeEach(() => {
    vi.mocked(knowledgeApi.list).mockResolvedValue([{
      id: 7,
      name: '员工制度',
      description: '公司制度与员工手册',
      createdBy: 1,
      documentCount: 3,
      readyDocumentCount: 2,
      createdAt: '2026-07-14T00:00:00Z',
      updatedAt: '2026-07-14T00:00:00Z',
    }])
  })

  it('renders the simple knowledge workspace and navigates into documents', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/knowledge-bases', component: KnowledgeListView },
        { path: '/knowledge-bases/:id/documents', component: { template: '<div>documents</div>' } },
      ],
    })
    await router.push('/knowledge-bases')
    await router.isReady()
    const wrapper = mount(KnowledgeListView, {
      attachTo: document.body,
      global: { plugins: [createPinia(), router, ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.get('h2').text()).toBe('企业知识库')
    expect(wrapper.text()).toContain('员工制度')
    expect(wrapper.text()).toContain('2 / 3 可用')
    expect(wrapper.get('[data-test="create-knowledge"]').text()).toContain('新建知识库')

    await wrapper.get('[data-test="enter-knowledge"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/knowledge-bases/7/documents')
  })
})
