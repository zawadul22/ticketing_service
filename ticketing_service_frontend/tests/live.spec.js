import { expect, test } from '@playwright/test'

test('live PostgreSQL-backed API is reachable through Vite', async ({ page, request }) => {
  test.skip(process.env.LIVE_API !== 'true', 'Set LIVE_API=true with the backend running on port 8080.')
  const health = await request.get('/api/health')
  expect(health.ok()).toBeTruthy()
  expect((await health.json()).status).toBe('UP')
  const response = page.waitForResponse(response => new URL(response.url()).pathname === '/api/events')
  await page.goto('/events')
  const events = await response
  expect(events.ok()).toBeTruthy()
  expect(Array.isArray(await events.json())).toBeTruthy()
  await expect(page.getByRole('heading', { name: 'Good plans. Great memories.' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'A little interruption.' })).toHaveCount(0)
  await page.screenshot({ path: `test-results/live-${test.info().project.name}.png`, fullPage: true })
})
