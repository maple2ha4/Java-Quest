import { test, expect, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import type { SourceFile } from '../../src/types'
const references = JSON.parse(
  readFileSync(
    new URL('../../../backend/src/test/resources/course-solutions.json', import.meta.url),
    'utf-8',
  ),
) as Record<string, SourceFile[]>
async function setCode(page: Page, value: string) {
  await page.locator('.monaco-editor textarea').focus()
  await page.keyboard.press('ControlOrMeta+A')
  // Paste a complete file as a learner would; typed XML '>' triggers automatic tag closing.
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write'])
  await page.evaluate((text) => navigator.clipboard.writeText(text), value)
  await page.keyboard.press('ControlOrMeta+V')
}
test('course samples: all six courses, real POM/Spring/business previews and no awarded progress', async ({
  page,
}) => {
  test.setTimeout(150000)
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/samples')
  await expect(page.locator('.sample-stage')).toHaveCount(6)
  await expect(page.locator('.sample-card')).toHaveCount(18)
  const catalog = await (await page.request.get('/api/curriculum')).json()
  const counts = new Set<number>(
    catalog.flatMap((s: any) =>
      s.chapters.flatMap((c: any) => c.topics.map((t: any) => t.exercises.length)),
    ),
  )
  expect(counts.size).toBeGreaterThan(3)
  await page.getByRole('link', { name: '登录', exact: true }).click()
  await page.getByRole('button', { name: '创建账户', exact: true }).click()
  await page.getByLabel('怎么称呼你').fill('课程体验者')
  await page
    .getByLabel('邮箱', { exact: true })
    .fill('samples-' + randomUUID().slice(0, 8) + '@example.com')
  await page.getByLabel('密码', { exact: true }).fill(randomUUID())
  await page.getByRole('button', { name: '创建账户，开始探索' }).click()
  await expect(page).toHaveURL((url) => url.pathname === '/')
  for (const id of ['pom-release', 'spring-qualifier', 'idempotent-ledger']) {
    await page.goto('/learn/' + id + '?preview=1')
    await expect(page.locator('.preview-notice')).toContainText('不计 XP')
    const lessons = await (await page.request.get('/api/lessons')).json()
    const lesson = lessons.find((l: any) => l.id === id)
    for (const file of references[id].filter((f) => lesson.editableFiles.includes(f.name))) {
      await page.getByRole('button', { name: file.name, exact: true }).click()
      await expect(page.locator('.editor-status')).toContainText('可编辑')
      await setCode(page, file.content)
    }
    await expect(page.locator('.editor-status')).toContainText('已保存到服务器')
    await page.getByRole('button', { name: '运行并验证', exact: true }).click()
    await expect(page.getByText('全部测试通过', { exact: true })).toBeVisible({ timeout: 45000 })
    await expect(page.locator('.completion')).toContainText('体验验证通过')
    await expect(page.locator('.completion')).toContainText('未计入进度')
    const profile = await (await page.request.get('/api/me')).json()
    expect(profile.xp).toBe(0)
    expect(profile.completed).toEqual([])
    expect(profile.submissions).toBe(0)
  }
  await page.goto('/learn/idempotent-ledger')
  await expect(page.getByRole('button', { name: '运行并验证', exact: true })).toBeDisabled()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/samples')
  await expect(page.locator('.sample-card')).toHaveCount(18)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  expect(errors).toEqual([])
})
