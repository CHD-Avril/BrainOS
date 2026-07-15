import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'

const fixture = fileURLToPath(new URL('../../fixtures/employee-handbook.md', import.meta.url))

test('completes knowledge import, Chroma indexing, grounded answer and citation', async ({ page }, testInfo) => {
  test.setTimeout(90_000)
  const suffix = testInfo.project.name.includes('1024') ? '1024' : '1440'
  const knowledgeName = `员工制度演示-${suffix}-${Date.now().toString(36)}`
  const consoleErrors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })

  await page.goto('/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('BrainOS@123')
  await page.getByRole('button', { name: '登录' }).click()

  await page.getByRole('menuitem', { name: '知识库' }).click()
  await page.getByRole('button', { name: '新建知识库' }).click()
  await page.getByLabel('知识库名称').fill(knowledgeName)
  await page.getByLabel('知识库描述').fill('答辩自动化演示知识库')
  await page.getByRole('dialog').getByRole('button', { name: '保存' }).click()

  const knowledgeCard = page.locator('.knowledge-card').filter({ hasText: knowledgeName })
  await expect(knowledgeCard).toBeVisible()
  await knowledgeCard.getByRole('button', { name: '进入知识库' }).click()

  await page.getByLabel('上传文档').setInputFiles(fixture)
  const documentRow = page.locator('.el-table__row').filter({ hasText: 'employee-handbook.md' })
  await expect(documentRow).toBeVisible()
  await expect(documentRow.getByText('可用', { exact: true })).toBeVisible({ timeout: 30_000 })
  await expect(documentRow.locator('td').nth(2)).not.toHaveText('—')

  await page.getByRole('menuitem', { name: 'AI 问答' }).click()
  await page.locator('.chat-toolbar__controls .el-select').first().click()
  await page.getByRole('option', { name: new RegExp(knowledgeName) }).click()
  await page.getByPlaceholder('输入你想从知识库中查询的问题…').fill('正式员工每年有几天年假，如何申请？')
  await page.locator('[data-test="send-question"]').click()

  const answer = page.locator('.assistant-message').last()
  await expect(answer).toContainText('5 天带薪年假', { timeout: 30_000 })
  const citation = answer.locator('details').filter({ hasText: '正式员工' })
  await expect(citation).toBeVisible()
  await citation.locator('summary').click()
  await expect(citation).toContainText('employee-handbook.md')
  await expect(citation).toContainText('正式员工')

  expect(await page.evaluate(() => (
    Math.max(document.documentElement.scrollWidth, document.body.scrollWidth) <= window.innerWidth
  ))).toBe(true)
  expect(consoleErrors).toEqual([])
})
