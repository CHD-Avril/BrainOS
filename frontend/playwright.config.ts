import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  reporter: 'line',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    channel: 'chrome',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [
    { name: 'chrome-1024x768', use: { viewport: { width: 1024, height: 768 } } },
    { name: 'chrome-1440x900', use: { viewport: { width: 1440, height: 900 } } },
  ],
  webServer: [
    {
      command: '../scripts/run-e2e-backend.sh',
      cwd: '.',
      url: 'http://127.0.0.1:8080/actuator/health',
      timeout: 120_000,
      reuseExistingServer: false,
    },
    {
      command: 'pnpm dev --host 127.0.0.1 --port 4173',
      cwd: '.',
      url: 'http://127.0.0.1:4173/login',
      timeout: 60_000,
      reuseExistingServer: false,
    },
  ],
})
