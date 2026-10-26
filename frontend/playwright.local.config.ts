import { defineConfig } from '@playwright/test'
import base from './playwright.config'
export default defineConfig(base, {
  testDir: './tests/local',
  outputDir: '../.artifacts/playwright-local',
})
