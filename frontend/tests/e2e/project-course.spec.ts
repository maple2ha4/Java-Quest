import { test, expect, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import type { Lesson, SourceFile } from '../../src/types'
const solutions = JSON.parse(
  readFileSync(
    new URL('../../../backend/src/test/resources/course-solutions.json', import.meta.url),
    'utf-8',
  ),
) as Record<string, SourceFile[]>
async function replace(page: Page, value: string) {
  await page.locator('.monaco-editor textarea').focus()
  await page.keyboard.press('ControlOrMeta+A')
  await page.keyboard.insertText(value)
}
test('project course: hierarchy, truthful planning, packaged multi-file changes and real completion', async ({
  page,
}) => {
  test.setTimeout(180000)
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/map?stage=maven')
  await expect(page.getByRole('heading', { name: 'Maven 与工程化', exact: true })).toBeVisible()
  await page.getByText('目录约定、坐标与 pom', { exact: true }).click()
  await expect(page.locator('.exercise-row.planned').first()).toContainText('规划中')
  await expect(page.locator('.exercise-row.planned a')).toHaveCount(0)
  await page.goto('/login')
  await page.getByRole('button', { name: '创建账户', exact: true }).click()
  await page.getByLabel('怎么称呼你').fill('项目学习者')
  await page
    .getByLabel('邮箱', { exact: true })
    .fill('project-' + randomUUID().slice(0, 8) + '@example.com')
  await page.getByLabel('密码', { exact: true }).fill(randomUUID())
  await page.getByRole('button', { name: '创建账户，开始探索' }).click()
  await expect(page).toHaveURL((url) => url.pathname === '/')
  const lessons = (await (await page.request.get('/api/lessons')).json()) as Lesson[]
  const byId = new Map(lessons.map((l) => [l.id, l]))
  const completed = new Set<string>()
  let last = 0
  async function solve(id: string) {
    if (completed.has(id)) return
    const lesson = byId.get(id)!
    for (const before of lesson.prerequisites) await solve(before)
    await new Promise((r) => setTimeout(r, Math.max(0, 2200 - (Date.now() - last))))
    last = Date.now()
    const response = await page.request.post('/api/submissions', {
      headers: { 'X-Quest-Client': '1' },
      data: { lessonId: id, files: solutions[id] },
    })
    expect(response.status(), await response.text()).toBe(200)
    expect((await response.json()).result.passed).toBe(true)
    completed.add(id)
  }
  for (const before of byId.get('order-release')!.prerequisites) await solve(before)
  await page.goto('/projects')
  await page.getByRole('link', { name: /完成订单报表与边界验证/ }).click()
  await expect(
    page.getByRole('heading', { name: '完成订单报表与边界验证', exact: true }),
  ).toBeVisible()
  const driver = 'src/main/java/quest/orders/Application.java'
  await page.getByRole('button', { name: driver, exact: true }).click()
  await expect(page.locator('.editor-status')).toContainText('只读调用方')
  const before = await page.locator('.view-lines').innerText()
  await replace(page, 'forged')
  await expect(page.locator('.view-lines')).toHaveText(before, { useInnerText: true })
  const original = (await (
    await page.request.get('/api/drafts/order-release')
  ).json()) as SourceFile[]
  const forged = original.map((f) => (f.name === driver ? { ...f, content: 'forged' } : f))
  expect(
    (
      await page.request.put('/api/drafts/order-release', {
        headers: { 'X-Quest-Client': '1' },
        data: { files: forged },
      })
    ).status(),
  ).toBe(400)
  await page.getByRole('button', { name: '添加文件', exact: true }).click()
  await page.getByLabel('新文件名').fill('src/main/java/quest/orders/Audit.java')
  await page.getByRole('button', { name: '添加', exact: true }).click()
  await expect(page.locator('.view-lines')).toContainText('package quest.orders;')
  await page.getByRole('button', { name: '删除文件', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.locator('.monaco-editor')).toBeVisible()
  for (const file of solutions['order-release'].filter((f) =>
    byId.get('order-release')!.editableFiles.includes(f.name),
  )) {
    await page.getByRole('button', { name: file.name, exact: true }).click()
    await expect(page.locator('.editor-status')).toContainText('可编辑')
    await replace(page, file.content)
  }
  await expect(page.locator('.editor-status')).toContainText('已保存到服务器')
  await page.reload()
  await expect(page.locator('.file-tabs button').filter({ hasText: driver })).toBeVisible()
  const draft = await (await page.request.get('/api/drafts/order-release')).json()
  expect(draft).toEqual(solutions['order-release'])
  await page.getByRole('button', { name: '运行并验证', exact: true }).click()
  await expect(page.getByText('全部测试通过', { exact: true })).toBeVisible({ timeout: 45000 })
  await expect(page.locator('.output-title')).toContainText('5 / 5')
  await expect(page.locator('.completion')).toContainText('+65 XP')
  await page.reload()
  const profile = await (await page.request.get('/api/me')).json()
  expect(profile.completed).toContain('order-release')
  expect(errors).toEqual([])
})
