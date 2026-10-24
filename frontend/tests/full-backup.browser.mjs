import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), server: { host: '127.0.0.1', port: 0 } });
let browser;
try {
  await server.listen();
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage({ acceptDownloads: true, viewport: { width: 1440, height: 1100 } });
  const errors = [];
  let exportCalls = 0;
  page.on('pageerror', error => errors.push(error.message));
  let restoreCalls = 0;
  let rekeyed = false;
  const provider = { providerId: 'openai', displayName: '备份模型', protocol: 'OPENAI_COMPATIBLE',
    baseUrl: 'https://example.test/v1', model: 'fixture-model', configured: false, maskedApiKey: null };
  const backup = { type: 'wealthflow-full-backup', version: 1, exportedAt: '2026-10-24T10:00:00Z',
    tables: { asset_category: [], knowledge_document: [], agent_conversation: [] } };
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    let data = [];
    if (path === '/system/info') data = { dbPath: 'temporary-test.db', categoryCount: 0, snapshotDateCount: 0 };
    if (path === '/agent/permission') data = { level: 'REVIEW' };
    if (path === '/agent/providers') data = restoreCalls >= 2 ? [{ ...provider, configured: rekeyed, maskedApiKey: rekeyed ? '已配置' : null }] : [];
    if (path === '/agent/providers/openai' && route.request().method() === 'PUT') {
      assert.equal(route.request().postDataJSON().apiKey, 'browser-fixture-key');
      rekeyed = true;
      data = { ...provider, configured: true, maskedApiKey: '已配置' };
    }
    if (path === '/knowledge/embedding') data = { mode: 'REUSE', model: '', configured: false, enabled: false };
    if (path === '/knowledge/index-status') data = { total: 0, indexed: 0, pending: 0, failed: 0, enabled: false };
    if (path === '/system/full-backup') { data = backup; exportCalls++; }
    if (path === '/system/full-restore') {
      restoreCalls++;
      if (restoreCalls === 1) {
        await route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ code: 40001, message: '恢复已回滚' }) });
        return;
      }
      data = true;
    }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) });
  });
  await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/settings`);
  const exportButton = page.getByRole('button', { name: /导出完整备份/ });
  await exportButton.scrollIntoViewIfNeeded({ timeout: 5000 });
  const downloadEvent = page.waitForEvent('download', { timeout: 10000 }).catch(() => null);
  await exportButton.click({ timeout: 5000 });
  await page.getByText('完整备份已导出', { exact: true }).waitFor({ timeout: 5000 });
  const download = await downloadEvent;
  assert.ok(download, `No download after ${exportCalls} export requests`);
  const exported = JSON.parse(await readFile(await download.path(), 'utf8'));
  assert.equal(exported.type, 'wealthflow-full-backup');
  assert.equal(exported.settings.theme, 'light');
  const restored = { ...exported, settings: { ...exported.settings, theme: 'dark', themeColor: '#13c2c2' } };
  await page.locator('input[aria-label="完整备份文件"]').setInputFiles({ name: 'full.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(restored)) });
  await page.getByRole('button', { name: '确认覆盖恢复', exact: true }).click();
  await page.getByText('恢复已回滚', { exact: true }).waitFor();
  assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('wealthflow_settings')).theme), 'light');
  await page.getByRole('button', { name: '确认覆盖恢复', exact: true }).click();
  await page.getByText('完整备份恢复成功', { exact: true }).waitFor();
  assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('wealthflow_settings')).theme), 'dark');
  await page.getByRole('button', { name: '刷新并使用恢复的数据', exact: true }).click();
  await page.getByText('完整备份与换机恢复', { exact: true }).waitFor();
  assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('wealthflow_settings')).themeColor), '#13c2c2');
  await page.getByText('待填写 API Key', { exact: true }).waitFor();
  await page.getByRole('button', { name: /编辑/ }).click();
  await page.getByPlaceholder('sk-...').fill('browser-fixture-key');
  await page.getByRole('button', { name: '保存配置', exact: true }).click();
  await page.getByText('提供商配置已保存', { exact: true }).waitFor();
  assert.equal(rekeyed, true);
  await page.getByText('完整备份与换机恢复', { exact: true }).scrollIntoViewIfNeeded();
  await page.screenshot({ path: 'node_modules/.cache/full-backup.png', animations: 'disabled' });
  assert.equal(restoreCalls, 2);
  assert.deepEqual(errors, []);
  console.log('完整备份浏览器验收通过：导出含设置、覆盖确认、失败不改设置、恢复主题、刷新保留、重新填写模型 Key。');
} finally { await browser?.close(); await server.close(); }
