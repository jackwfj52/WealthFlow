// 独立浏览器与模拟API；验证新回复、历史会话和安全来源链接。
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
  const conversation = { id: 'news', title: '财经讯息测试', providerId: 'test', createdAt: '', updatedAt: '' };
  const response = { reply: '报道内容可查阅来源[资讯:0123456789abcdef]。', steps: ['查询财经讯息：2条'], sources: [],
    newsSources: [
      { id: '0123456789abcdef', title: '测试新闻标题', summary: '<script>不会执行</script>普通摘要', publisher: '测试媒体',
        publishedAt: '2026-10-24T10:00:00+08:00', retrievedAt: '2026-10-24T14:00:00+08:00', url: 'https://finance.eastmoney.com/a/test.html' },
      { id: 'fedcba9876543210', title: '无效链接保留文本', summary: '', publisher: '测试媒体',
        publishedAt: '2026-10-24T09:00:00+08:00', retrievedAt: '2026-10-24T14:00:00+08:00', url: 'javascript:alert(1)' },
    ], webSources: [
      { id: 'web-0123456789abcdef', title: '公司官网公告', snippet: '<script>网页摘要不会执行</script>公开信息',
        url: 'https://example.com/release', siteName: 'example.com', publishedAt: null, retrievedAt: '2026-10-25T00:00:00+08:00' },
      { id: 'web-fedcba9876543210', title: '不安全网页链接', snippet: '',
        url: 'javascript:alert(1)', siteName: 'example.com', publishedAt: null, retrievedAt: '2026-10-25T00:00:00+08:00' },
    ] };
  const saved = [{ id: 'old', role: 'assistant', content: '旧会话仍可读取', response: { reply: '旧会话仍可读取', steps: [] }, createdAt: '' }];
  let sends = 0;
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    let data = [];
    if (path === '/agent/conversations') data = [conversation];
    if (path === '/agent/conversations/news') data = { conversation, messages: saved, hasMore: false };
    if (path === '/agent/providers') data = [{ providerId: 'test', displayName: '测试模型', model: 'test', configured: true }];
    if (path === '/agent/permission') data = { level: 'READ_ONLY' };
    if (path === '/agent/conversations/news/messages/stream') {
      sends++;
      data = { id: `answer-${sends}`, role: 'assistant', content: response.reply, response, createdAt: '' };
      saved.push(data);
    }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, message: '', data }) });
  });
  await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/agent`);
  await page.getByText('旧会话仍可读取', { exact: true }).waitFor();
  await page.locator('.ant-input-search input').fill('查询最近三天英伟达的新闻');
  await page.getByRole('button', { name: /发送/ }).click();
  await page.getByText('财经讯息来源 (2)', { exact: true }).click();
  const link = page.getByRole('link', { name: '测试新闻标题', exact: true });
  await link.waitFor();
  assert.equal(await link.getAttribute('href'), 'https://finance.eastmoney.com/a/test.html');
  assert.equal(await link.getAttribute('rel'), 'noopener noreferrer');
  assert.equal(await page.getByRole('link', { name: '无效链接保留文本' }).count(), 0);
  assert.equal(await page.locator('script').filter({ hasText: '不会执行' }).count(), 0);
  await page.getByText('发布时间：2026-10-24 10:00:00+08:00', { exact: true }).waitFor();
  await page.getByText('联网搜索来源 (2)', { exact: true }).click();
  const webLink = page.getByRole('link', { name: '公司官网公告', exact: true });
  await webLink.waitFor();
  assert.equal(await webLink.getAttribute('href'), 'https://example.com/release');
  assert.equal(await webLink.getAttribute('rel'), 'noopener noreferrer');
  assert.equal(await page.getByRole('link', { name: '不安全网页链接' }).count(), 0);
  assert.equal(await page.locator('script').filter({ hasText: '网页摘要不会执行' }).count(), 0);
  assert.equal(await page.getByText('发布时间：搜索结果未提供', { exact: true }).count(), 2);
  assert.equal(sends, 1);
  await page.reload();
  await page.getByText('财经讯息来源 (2)', { exact: true }).click();
  await link.waitFor();
  await page.getByText('联网搜索来源 (2)', { exact: true }).click();
  await webLink.waitFor();
  await webLink.scrollIntoViewIfNeeded();
  await page.screenshot({ path: 'node_modules/.cache/financial-news-chat.png', fullPage: true, animations: 'disabled' });
  assert.deepEqual(errors, []);
  console.log('财经与联网搜索浏览器检查通过：新回复来源、未知发布时间、原文链接、旧会话兼容、刷新保留与无效链接处理。');
} finally { await browser?.close(); await server.close(); }
