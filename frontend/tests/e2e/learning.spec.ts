import { test, expect, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'

async function code(page: Page, content: string) {
  await page.locator('.monaco-editor textarea').focus()
  await page.keyboard.press('ControlOrMeta+A')
  await page.keyboard.insertText(content)
  await expect(page.locator('.view-lines')).toContainText(content)
  await expect(page.locator('.result-banner')).toHaveCount(0)
}
const hello =
  'public class Main { public static void main(String[] args) { System.out.println("Hello, Java Quest!"); } }'

test('real learning loop, drafts, notes and persistent progress', async ({ page }, testInfo) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  const publishedCount = (await (await page.request.get('/api/lessons')).json()).length
  await page.goto('/learn/hello-world')
  if (testInfo.project.name === 'process') {
    const capabilities = await (await page.request.get('/api/capabilities')).json()
    expect(capabilities.judgeMode).toBe('process')
    await expect(page.locator('.app-mode-notice')).toContainText('没有文件、网络或进程隔离')
  }
  const guestCode =
    'public class Main { public static void main(String[] args) { System.out.println("Hello, learner!"); } } // guest draft'
  await page.locator('.monaco-editor').waitFor()
  await code(page, guestCode)
  await page.getByRole('button', { name: '登录并运行', exact: true }).click()
  await page.getByRole('button', { name: '创建账户', exact: true }).click()
  await page.getByLabel('怎么称呼你').fill('浏览器学习者')
  await page
    .getByLabel('邮箱', { exact: true })
    .fill('browser-' + randomUUID().slice(0, 8) + '@example.com')
  await page.getByLabel('密码', { exact: true }).fill(randomUUID())
  await page.getByRole('button', { name: '创建账户，开始探索' }).click()
  await expect(page).toHaveURL((url) => url.pathname === '/learn/hello-world')
  await page.getByRole('button', { name: '导入未登录草稿', exact: true }).click()
  await expect(page.locator('.view-lines')).toContainText('guest draft')
  await page.locator('.monaco-editor').waitFor()
  await page.getByRole('button', { name: '运行并验证', exact: true }).click()
  await expect(page.getByText('输出与目标不一致', { exact: true })).toBeVisible({ timeout: 30000 })
  await expect(page.locator('.completion')).toHaveCount(0)
  await code(page, 'public class Main { BAD }')
  await page.getByRole('button', { name: '运行并验证', exact: true }).click()
  await expect(page.getByText('编译没有通过', { exact: true })).toBeVisible({ timeout: 30000 })
  await code(page, hello)
  await expect(page.locator('.editor-status')).toContainText('已保存到服务器')
  await page.reload()
  await page.locator('.monaco-editor').waitFor()
  await expect(page.locator('.view-lines')).toContainText('Hello, Java Quest!')
  await page.getByRole('button', { name: '运行并验证', exact: true }).click()
  await expect(page.getByText('全部测试通过', { exact: true })).toBeVisible({ timeout: 30000 })
  await expect(page.locator('.completion')).toContainText('+30 XP')
  await page.getByText('记下你的理解', { exact: true }).click()
  await page.getByLabel('本关学习笔记').fill('main 是入口，println 会换行。')
  await page.getByRole('button', { name: '保存笔记', exact: true }).click()
  await expect(page.locator('.note-editor [role=status]')).toContainText('笔记已保存')
  await page.screenshot({ path: '../.artifacts/hello-completed.png', fullPage: true })
  await page.getByRole('link', { name: '下一关', exact: true }).click()
  await expect(page.getByRole('heading', { name: '修复多行输出', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.locator('.sidebar-course')).toContainText(`1 / ${publishedCount}`)
  await page.goto('/profile')
  await expect(page.locator('.stat-card').first()).toContainText(`1 / ${publishedCount}`)
  await page.goto('/review')
  await expect(page.locator('.review-card')).toHaveCount(2)
  await page.getByRole('button', { name: /学习笔记/ }).click()
  await expect(page.locator('.note-body')).toContainText('main 是入口')
  expect(errors).toEqual([])
})

test('responsive pages, theme, and locked submission', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  for (const path of ['/', '/map', '/projects', '/profile', '/review', '/learn/hello-world']) {
    await page.goto(path)
    await expect(page.locator('.load-state')).toHaveCount(0)
    await expect(page.locator('h1').first()).toBeVisible()
  }
  await page.getByRole('button', { name: '切换深色主题' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.screenshot({ path: '../.artifacts/hello-dark.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '每一行，都是进步。' })).toBeVisible()
  await page.screenshot({ path: '../.artifacts/home-mobile.png', fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  await page.goto('/learn/variables')
  await expect(page.getByRole('button', { name: '登录并运行', exact: true })).toBeDisabled()
  await page.screenshot({ path: '../.artifacts/lesson-mobile.png', fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  expect(errors).toEqual([])
})
