import { beforeEach, vi } from 'vitest'

beforeEach(() => {
  vi.stubGlobal('uni', {
    getStorageSync: vi.fn(), setStorageSync: vi.fn(), removeStorageSync: vi.fn(),
    request: vi.fn(), uploadFile: vi.fn(), navigateTo: vi.fn(), navigateBack: vi.fn(),
    redirectTo: vi.fn(), reLaunch: vi.fn(), switchTab: vi.fn(), showToast: vi.fn(),
    showModal: vi.fn(), chooseMessageFile: vi.fn(), stopPullDownRefresh: vi.fn(),
  })
})
