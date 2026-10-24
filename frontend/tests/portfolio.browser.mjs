// 验证两组持仓依据在聊天中展示不同占比，刷新保留，兼容无依据的历史回复。
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), server: { host: '127.0.0.1', port: 0 } });
let browser;
try {
  await server.listen();
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const conversations = ['high', 'low'].map(id => ({ id, title: id === 'high' ? '科技持仓60%' : '科技持仓5%', providerId: null, createdAt: '', updatedAt: '' }));
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    let data = [];
    if (path === '/agent/conversations') data = conversations;
    if (path === '/agent/permission') data = { level: 'READ_ONLY' };
    if (path === '/agent/conversations/high' || path === '/agent/conversations/low') {
      const id = path.split('/').at(-1); const pct = id === 'high' ? 60 : 5;
      const context = { id: `portfolio-${id}`, retrievedAt: '2026-10-24T15:00:00+08:00', snapshotDate: '2026-10-24', snapshotAgeDays: 0,
        registeredPortfolioPercent: 50, sectorTaggedPortfolioPercent: 50,
        categories: [{ categoryId: '1', categoryName: '股票', portfolioPercent: 50, registeredPercent: 100, unclassifiedPercent: 0,
          holdings: [], sectorExposure: [{ sector: 'TECH', label: '科技', categoryPercent: pct, estimatedPortfolioPercent: pct / 2 }] }],
        limitations: ['现金金额不等于可投资闲钱，需核对应急金和支出。'] };
      data = { conversation: conversations.find(item => item.id === id), hasMore: false, messages: [
        { id: 'old', role: 'assistant', content: '旧回复', response: { reply: '旧回复', steps: [] } },
        { id: 'new', role: 'assistant', content: `根据已登记的科技仓位分析[持仓:portfolio-${id}]`,
          response: { reply: '持仓分析', steps: ['查询当前持仓与资产配置'], portfolioContext: context } },
      ] };
    }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, message: '', data }) });
  });
  await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/agent`);
  await page.getByText('持仓分析依据', { exact: true }).click();
  await page.getByText('科技：占本类 60% · 估算占总资产 30%', { exact: true }).waitFor();
  await page.getByRole('button', { name: '科技持仓5%', exact: true }).click();
  await page.getByText('持仓分析依据', { exact: true }).click();
  await page.getByText('科技：占本类 5% · 估算占总资产 2.5%', { exact: true }).waitFor();
  await page.reload();
  await page.getByText('持仓分析依据', { exact: true }).click();
  await page.getByText('科技：占本类 5% · 估算占总资产 2.5%', { exact: true }).waitFor();
  await page.screenshot({ path: 'node_modules/.cache/portfolio-basis.png', fullPage: true, animations: 'disabled' });
  assert.deepEqual(errors, []);
  console.log('持仓依据浏览器检查通过：60%/5%分别展示、总资产分母、切换会话、刷新保留与旧消息兼容。');
} finally { await browser?.close(); await server.close(); }
