import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');

const conversation = { id: 'live', title: '实时进度测试', providerId: 'test', createdAt: '', updatedAt: '' };
const context = { id: 'binance-test', provider: 'Binance Spot', retrievedAt: '2026-10-25T01:00:00Z', days: 7,
  coverage: '浏览器模拟数据；只验证界面，不代表真实行情。', markets: [{ symbol: 'ZECUSDT', quoteAsset: 'USDT', lastPrice: '100',
    changePercent24h: '2.5', high24h: '105', low24h: '95', quoteVolume24h: '100000',
    periodStartTime: '2026-10-18T00:00:00Z', periodEndTime: '2026-10-24T23:59:59Z', periodChangePercent: '5',
  }] };
const response = { reply: '以下是模拟行情回答[行情:binance-test]。', steps: ['查询币安现货行情：ZECUSDT'], cryptoMarkets: [context] };
const saved = [];
let release;
let sends = 0;
let failNext = false;
const event = (name, data) => `event: ${name}\ndata: ${JSON.stringify(data)}\n\n`;
const server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)),
  server: { host: '127.0.0.1', port: 0 }, plugins: [{ name: 'local-agent-stream-fixture', configureServer(vite) {
    vite.middlewares.use('/api/v1/agent/conversations/live/messages/stream', (req, res) => {
      sends++;
      req.resume();
      res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', 'X-Accel-Buffering': 'no' });
      res.write(event('progress', { id: 'model', tool: 'model', label: '分析问题并规划查询', status: 'completed', detail: '已收到模型结果', at: '' }));
      res.write(event('progress', { id: 'binance', tool: 'query_crypto_market', label: '查询币安现货价格与日K线', status: 'running', detail: '正在调用工具', at: '' }));
      if (failNext) {
        res.write(event('progress', { id: 'binance', tool: 'query_crypto_market', label: '查询币安现货价格与日K线', status: 'failed', detail: '币安接口超时', at: '' }));
        res.end(event('error', { code: 50014, message: '币安接口超时，请稍后重试' }));
        return;
      }
      const heartbeat = setInterval(() => res.write(event('heartbeat', { at: new Date().toISOString() })), 500);
      release = () => {
        clearInterval(heartbeat);
        res.write(event('progress', { id: 'binance', tool: 'query_crypto_market', label: '查询币安现货价格与日K线', status: 'completed', detail: '已取得 ZECUSDT 行情', at: '' }));
        const data = { id: 'answer', role: 'assistant', content: response.reply, response, createdAt: '' };
        saved.push(data);
        res.end(event('result', data));
      };
      res.on('close', () => clearInterval(heartbeat));
    });
  } }] });
let browser;
try {
  await server.listen();
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path.endsWith('/messages/stream')) return route.continue();
    let data = [];
    if (path === '/agent/conversations') data = [conversation];
    if (path === '/agent/conversations/live') data = { conversation, messages: saved, hasMore: false };
    if (path === '/agent/providers') data = [{ providerId: 'test', displayName: '测试模型', model: 'test', configured: true }];
    if (path === '/agent/permission') data = { level: 'READ_ONLY' };
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, message: '', data }) });
  });
  await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/agent`);
  await page.locator('.ant-input-search input').fill('ZEC最近表现如何？');
  await page.getByRole('button', { name: /发送/ }).click();
  await page.getByRole('status', { name: '助手处理进度' }).waitFor();
  await page.getByText('正在调用工具', { exact: true }).waitFor();
  await page.getByText(/已用时 [1-9]\d* 秒/).waitFor();
  assert.equal(await page.getByText(response.reply, { exact: false }).count(), 0);
  assert.equal(sends, 1);
  await page.screenshot({ path: 'node_modules/.cache/agent-live-progress.png', fullPage: true, animations: 'disabled' });
  assert.equal(typeof release, 'function');
  release();
  await page.getByText(response.reply, { exact: false }).waitFor();
  await page.getByText('币安行情依据 (1)', { exact: true }).click();
  await page.getByRole('link', { name: 'ZECUSDT', exact: true }).waitFor();
  await page.getByText('最近 7 个已完成 UTC 日：5%', { exact: true }).waitFor();
  await page.reload();
  await page.getByText('币安行情依据 (1)', { exact: true }).click();
  await page.getByRole('link', { name: 'ZECUSDT', exact: true }).waitFor();
  failNext = true;
  await page.locator('.ant-input-search input').fill('再查一次ZEC行情');
  await page.getByRole('button', { name: /发送/ }).click();
  await page.getByText('币安接口超时，请稍后重试', { exact: true }).first().waitFor();
  await page.getByText('处理过程 (2步)', { exact: true }).click();
  await page.getByText('币安接口超时', { exact: true }).waitFor();
  assert.equal(sends, 2);
  assert.deepEqual(errors, []);
  console.log('实时进度浏览器检查通过：最终回答前可见真实事件、计时与心跳，行情来源刷新保留，失败保留步骤且不重复提交。');
} finally { release?.(); await browser?.close(); await server.close(); }
