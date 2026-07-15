import { expect, test } from '@playwright/test'

test('admin can inspect the dashboard, create a user and trace the audit', async ({ page }, testInfo) => {
  const browserSuffix = testInfo.project.name.replace(/[^a-z0-9-]/g, '-').slice(0, 20)
  const username = `e2e.${browserSuffix}`
  const displayName = `答辩演示 ${testInfo.project.name}`
  const consoleErrors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })

  await page.goto('/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('BrainOS@123')
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByText('最近 7 天提问趋势', { exact: true })).toBeVisible()
  await expect(page.locator('.metric-card')).toHaveCount(4)

  await page.getByRole('menuitem', { name: '用户管理' }).click()
  await expect(page).toHaveURL(/\/admin\/users$/)
  await page.getByRole('button', { name: '新建用户' }).click()
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('显示名称').fill(displayName)
  await page.getByLabel('用户密码').fill('DefensePass8')
  await page.getByRole('dialog').getByRole('button', { name: '保存' }).click()
  await expect(page.getByText(displayName, { exact: true })).toBeVisible()

  await page.getByRole('menuitem', { name: '操作日志' }).click()
  await expect(page).toHaveURL(/\/admin\/audit-logs$/)
  await expect(page.getByText('创建用户', { exact: true }).first()).toBeVisible()
  await expect(page.getByText(`创建用户：${username}`, { exact: true })).toBeVisible()

  expect(await page.evaluate(() => (
    Math.max(document.documentElement.scrollWidth, document.body.scrollWidth) <= window.innerWidth
  ))).toBe(true)
  expect(consoleErrors).toEqual([])
})
