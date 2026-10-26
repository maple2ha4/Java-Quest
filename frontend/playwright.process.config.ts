import { defineConfig } from '@playwright/test'
import base from './playwright.config'
export default defineConfig(base, {
  projects: [{ name: 'process' }],
  grep: /real learning loop/,
  outputDir: '../.artifacts/playwright-process',
})
