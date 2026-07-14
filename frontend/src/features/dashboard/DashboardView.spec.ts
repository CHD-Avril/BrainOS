import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardView from './DashboardView.vue'
import { dashboardApi } from './api'

vi.mock('./api', () => ({
  dashboardApi: {
    summary: vi.fn(),
    trend: vi.fn(),
    recentDocuments: vi.fn(),
  },
}))

describe('DashboardView', () => {
  beforeEach(() => {
    vi.mocked(dashboardApi.summary).mockResolvedValue({
      knowledgeBaseCount: 3,
      documentCount: 12,
      chunkCount: 86,
      questionCount: 24,
    })
    vi.mocked(dashboardApi.trend).mockResolvedValue([
      { date: '2026-07-14', count: 4 },
    ])
    vi.mocked(dashboardApi.recentDocuments).mockResolvedValue([{
      id: 9,
      knowledgeBaseId: 7,
      knowledgeBaseName: '人力资源',
      originalName: '员工手册.pdf',
      status: 'READY',
      updatedAt: '2026-07-14T08:00:00Z',
    }])
  })

  it('renders four metrics, the seven-day section and recent documents', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/dashboard', component: DashboardView },
        { path: '/chat', component: { template: '<div>chat</div>' } },
      ],
    })
    await router.push('/dashboard')
    const wrapper = mount(DashboardView, {
      global: {
        plugins: [router, ElementPlus],
        stubs: { TrendChart: { template: '<div data-test="trend-chart" />' } },
      },
    })
    await flushPromises()

    expect(wrapper.get('h2').text()).toBe('工作台')
    expect(wrapper.findAll('.metric-card')).toHaveLength(4)
    expect(wrapper.text()).toContain('86')
    expect(wrapper.text()).toContain('最近 7 天提问趋势')
    expect(wrapper.text()).toContain('员工手册.pdf')
    expect(wrapper.text()).toContain('人力资源')
  })
})
