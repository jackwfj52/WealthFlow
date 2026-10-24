// Read-only browser check against the running Docker deployment; no mocked API.
import assert from 'node:assert/strict';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = new URL(process.env.DOCKER_BASE_URL ?? 'http://127.0.0.1:18000');
assert.ok(['127.0.0.1', 'localhost'].includes(base.hostname));
const browser = await chromium.launch({ channel: 'msedge', headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  const errors = [];
  const badResponses = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('response', response => { if (response.status() >= 400) badResponses.push(`${response.status()} ${response.url()}`); });
  await page.goto(base.href);
  await page.getByText('WealthFlow', { exact: true }).waitFor();
  await page.goto(new URL('/settings', base).href);
  await page.getByText('完整备份与换机恢复', { exact: true }).waitFor();
  await page.getByText('/app/data/wealthflow.db', { exact: true }).waitFor();
  await page.reload();
  await page.getByText('/app/data/wealthflow.db', { exact: true }).waitFor();
  await page.screenshot({ path: 'node_modules/.cache/docker-settings.png', fullPage: true, animations: 'disabled' });
  assert.deepEqual(errors, []);
  assert.deepEqual(badResponses, []);
  console.log('Docker browser check passed: real frontend/API, settings, SPA reload, zero page errors.');
} finally { await browser.close(); }
