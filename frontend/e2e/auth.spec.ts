import { expect, test, type Page } from '@playwright/test'

async function assertResponsiveAndQuiet(page: Page, errors: string[]): Promise<void> {
  expect(errors, `browser console errors: ${errors.join('\n')}`).toEqual([])
  await expect(page.locator('#app')).toBeVisible()
  await expect(page.locator('#app')).not.toBeEmpty()
  await expect(page.getByRole('main')).toBeVisible()
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

test('ordinary user is guarded from admin routes and admin navigation', async ({ page, request }, testInfo) => {
  const consoleErrors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })

  const suffix = testInfo.project.name.includes('1024') ? '1024' : '1440'
  const username = `guard.${suffix}.${Date.now().toString(36)}`
  const password = 'OrdinaryPass8'
  const adminLogin = await request.post('http://127.0.0.1:8080/api/v1/auth/login', {
    data: { username: 'admin', password: 'BrainOS@123' },
  })
  expect(adminLogin.ok()).toBe(true)
  const adminSession = (await adminLogin.json()).data
  const created = await request.post('http://127.0.0.1:8080/api/v1/admin/users', {
    headers: { Authorization: `Bearer ${adminSession.accessToken}` },
    data: { username, displayName: '普通用户', password, role: 'USER' },
  })
  expect(created.ok()).toBe(true)

  await page.goto('/login')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)

  await page.goto('/admin/users')
  await expect(page).toHaveURL(/\/dashboard$/)
  const navigation = page.getByRole('navigation', { name: '主导航' })
  await expect(navigation).toBeVisible()
  await expect(page.getByRole('heading', { name: '工作台', exact: true, level: 1 })).toBeVisible()
  const main = page.getByRole('main')
  await expect(main.getByRole('heading', { name: '工作台', exact: true, level: 2 })).toBeVisible()
  await expect(main.getByText('一眼了解知识库资产与 AI 使用情况。', { exact: true })).toBeVisible()
  await expect(navigation.getByText('用户管理', { exact: true })).toHaveCount(0)
  await expect(navigation.getByText('操作日志', { exact: true })).toHaveCount(0)
  await assertResponsiveAndQuiet(page, consoleErrors)
})
