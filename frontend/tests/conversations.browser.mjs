// 运行前安装playwright，或通过PLAYWRIGHT_MODULE指定其ESM入口；使用独立无头浏览器和模拟API。
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)),
  server: { host: '127.0.0.1', port: 0 } });
let browser;
try {
  await server.listen();
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const conversation = id => ({ id, title: `会话${id}`, providerId: null, createdAt: '', updatedAt: '' });
  const message = (id, content) => ({ id, content, role: 'user', response: null, actionId: null, createdAt: '' });
  let olderRequests = 0;
  let releaseOlder;
  let markOlderArrived;
  const olderArrived = new Promise(resolve => { markOlderArrived = resolve; });
  let olderGate = new Promise(resolve => { releaseOlder = resolve; });
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    let data;
    if (path === '/agent/conversations') data = [conversation('A'), conversation('B')];
    else if (path === '/agent/conversations/A') {
      if (url.searchParams.get('page') === '1') {
        olderRequests++;
        markOlderArrived();
        await olderGate;
        // 包含一个重叠ID，验证分页偏移时不会重复插入。
        data = { conversation: conversation('A'), messages: [message('older-A', 'A的早期消息'), message('latest-A', 'A的最新消息')], hasMore: false };
      } else data = { conversation: conversation('A'), messages: [message('latest-A', 'A的最新消息')], hasMore: true };
    } else if (path === '/agent/conversations/B') {
      data = { conversation: conversation('B'), messages: [message('latest-B', 'B的最新消息')], hasMore: false };
    } else if (path === '/agent/permission') data = { level: 'REVIEW' };
    else data = [];
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, message: '', data }) });
  });
  const address = server.httpServer.address();
  await page.goto(`http://127.0.0.1:${address.port}/agent`);
  await page.getByText('A的最新消息', { exact: true }).waitFor();
  const older = page.getByRole('button', { name: '加载更早消息' });
  await older.evaluate(button => { button.click(); button.click(); });
  await olderArrived;
  await page.getByRole('button', { name: '会话B', exact: true }).click();
  await page.getByText('B的最新消息', { exact: true }).waitFor();
  const olderResponse = page.waitForResponse(response => response.url().includes('/A?page=1'));
  releaseOlder();
  await olderResponse;
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  assert.equal(olderRequests, 1, '双击不能发出重复分页请求');
  assert.equal(await page.getByText('A的早期消息', { exact: true }).count(), 0, '旧请求不能混入新会话');
  assert.equal(await page.getByText('B的最新消息', { exact: true }).count(), 1);

  // 返回A再次加载，确认锁已释放、旧数据正常插入并去重。
  olderGate = Promise.resolve();
  await page.getByRole('button', { name: '会话A', exact: true }).click();
  await page.getByText('A的最新消息', { exact: true }).waitFor();
  await page.getByRole('button', { name: '加载更早消息' }).click();
  await page.getByText('A的早期消息', { exact: true }).waitFor();
  assert.equal(await page.getByText('A的最新消息', { exact: true }).count(), 1, '分页重叠消息应去重');
  assert.equal(olderRequests, 2);
  assert.deepEqual(errors, []);
  console.log('会话浏览器回归通过：双击防重、切换会话隔离、重新加载和消息去重，无页面异常。');
} finally {
  await browser?.close();
  await server.close();
}
