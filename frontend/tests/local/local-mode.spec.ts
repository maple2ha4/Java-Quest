import { test, expect } from '@playwright/test'
import { randomUUID } from 'node:crypto'

test('local study mode saves real drafts and notes without executing or awarding progress', async ({
  page,
}) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/learn/hello-world')
  await expect(page.locator('.app-mode-notice')).toContainText('隔离判题未启用')
  await expect(page.getByRole('button', { name: '判题未启用', exact: true })).toBeDisabled()
  await page.getByRole('link', { name: '登录', exact: true }).click()
  await page.getByRole('button', { name: '创建账户', exact: true }).click()
  await page.getByLabel('怎么称呼你').fill('本地学习者')
  await page
    .getByLabel('邮箱', { exact: true })
    .fill('local-browser-' + randomUUID().slice(0, 8) + '@example.com')
  await page.getByLabel('密码', { exact: true }).fill(randomUUID())
  await page.getByRole('button', { name: '创建账户，开始探索' }).click()
  await expect(page).toHaveURL(/\/$/)
  await page.goto('/learn/hello-world')
  await page.locator('.monaco-editor textarea').focus()
  await page.keyboard.press('ControlOrMeta+A')
  await page.keyboard.insertText('public class Main {} // local draft')
  await expect(page.locator('.editor-status')).toContainText('已保存到服务器')
  await page.getByText('记下你的理解', { exact: true }).click()
  await page.getByLabel('本关学习笔记').fill('本地模式也会保存笔记。')
  await page.getByRole('button', { name: '保存笔记', exact: true }).click()
  await expect(page.locator('.note-editor [role=status]')).toContainText('笔记已保存')
  await page.reload()
  await expect(page.locator('.view-lines')).toContainText('local draft')
  await expect(page.getByRole('button', { name: '判题未启用', exact: true })).toBeDisabled()
  const response = await page.request.post('/api/submissions', {
    headers: { 'X-Quest-Client': '1' },
    data: {
      lessonId: 'hello-world',
      files: [{ name: 'Main.java', content: 'public class Main {}' }],
    },
  })
  expect(response.status()).toBe(503)
  const profile = await (await page.request.get('/api/me')).json()
  expect(profile.completed).toEqual([])
  expect(profile.xp).toBe(0)
  expect(profile.submissions).toBe(0)
  await page.goto('/review')
  await page.getByRole('button', { name: /学习笔记/ }).click()
  await expect(page.locator('.note-body')).toContainText('本地模式也会保存笔记')
  expect(errors).toEqual([])
})
