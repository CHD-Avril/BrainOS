import { expect, test, type Page } from '@playwright/test'

async function assertResponsiveAndQuiet(page: Page, errors: string[]): Promise<void> {
  expect(errors, `browser console errors: ${errors.join('\n')}`).toEqual([])
  await expect.poll(() => page.evaluate(() => (
    Math.max(document.documentElement.scrollWidth, document.body.scrollWidth) <= window.innerWidth
  ))).toBe(true)
  await expect(page).toHaveTitle('BrainOS')
  await expect(page.locator(
    '.el-overlay:visible, .el-loading-mask:visible, vite-error-overlay',
  )).toHaveCount(0)
}

test('real admin login exposes navigation and logout clears the session', async ({ page }) => {
  const consoleErrors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })

  await page.goto('/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('BrainOS@123')
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page).toHaveURL(/\/dashboard$/)
  const navigation = page.getByRole('navigation', { name: '主导航' })
  await expect(navigation).toBeVisible()
  await expect(navigation.getByText('工作台', { exact: true })).toBeVisible()
  await expect(navigation.getByText('知识库', { exact: true })).toBeVisible()
  await expect(navigation.getByText('AI 问答', { exact: true })).toBeVisible()
  await expect(navigation.getByText('用户管理', { exact: true })).toBeVisible()
  await expect(navigation.getByText('操作日志', { exact: true })).toBeVisible()
  await assertResponsiveAndQuiet(page, consoleErrors)

  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('brainos.auth.session')))
    .toBeNull()
})

test('ordinary user is guarded from admin routes and admin navigation', async ({ page }) => {
  const consoleErrors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  await page.addInitScript(() => {
    sessionStorage.setItem('brainos.auth.session', JSON.stringify({
      accessToken: 'e2e-user-access',
      refreshToken: 'e2e-user-refresh',
      user: { id: 42, username: 'ordinary-user', displayName: 'Ordinary User', role: 'USER' },
    }))
  })
  await page.goto('/admin/users')
  await expect(page).toHaveURL(/\/dashboard$/)
  const navigation = page.getByRole('navigation', { name: '主导航' })
  await expect(navigation.getByText('用户管理', { exact: true })).toHaveCount(0)
  await expect(navigation.getByText('操作日志', { exact: true })).toHaveCount(0)
  await assertResponsiveAndQuiet(page, consoleErrors)
})
