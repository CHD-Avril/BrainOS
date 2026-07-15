import { createRequire } from 'node:module'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

const uni = createRequire(import.meta.url)('@dcloudio/vite-plugin-uni').default as typeof import('@dcloudio/vite-plugin-uni').default

export default defineConfig(({ mode }) => ({
  plugins: mode === 'test' ? [] : [uni()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    clearMocks: true,
    restoreMocks: true,
  },
}))
