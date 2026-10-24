// 独立浏览器与模拟API，不修改实际资产数据。运行：node tests/holdings.browser.mjs
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
  const categories = ['基金', '股票', '理财', '加密货币', '贵金属', '现金'].map((name, index) => ({
    id: String(index + 1), name, color: ['#1677ff', '#52c41a', '#13c2c2', '#722ed1', '#faad14', '#fa8c16'][index], createdAt: '2026-10-24',
  }));
  let holdings = [
    { id: 'a', categoryId: '1', name: '纳斯达克', percentage: 60 },
    { id: 'b', categoryId: '1', name: '标普500', percentage: 30 },
    { id: 'c', categoryId: '2', name: '英伟达', percentage: 40 },
    { id: 'd', categoryId: '5', name: '黄金', percentage: 70 },
    { id: 'e', categoryId: '4', name: 'BTC', percentage: 80 },
  ];
  let saves = 0;
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    let data = [];
    if (path === '/categories') data = categories;
    if (path === '/holdings') data = holdings;
    if (path.startsWith('/holdings/') && route.request().method() === 'PUT') {
      saves++;
      const categoryId = path.split('/').at(-1);
      data = route.request().postDataJSON().items.map((item, index) => ({ ...item, categoryId, id: `${saves}-${index}` }));
      holdings = [...holdings.filter(item => item.categoryId !== categoryId), ...data];
    }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, message: '', data }) });
  });
  await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/holdings`);
  await page.getByRole('heading', { name: '具体持仓' }).waitFor();
  await page.getByText('纳斯达克', { exact: true }).waitFor();
  assert.equal(await page.getByRole('menuitem', { name: '具体持仓' }).count(), 1);
  const card = title => page.locator('.ant-card').filter({ has: page.locator('.ant-card-head-title').getByText(title, { exact: true }) });
  const dialog = page.getByRole('dialog');
  for (const category of categories) {
    assert.equal(await card(`${category.name}持仓`).count(), 1, `${category.name}应使用自己的类别名称`);
    assert.equal(await card(`${category.name}持仓`).locator('.ant-tag').textContent(), category.name);
  }
  await card('理财持仓').getByText('例如：银行理财产品', { exact: true }).waitFor();
  assert.equal(await card('理财持仓').getByText('黄金', { exact: true }).count(), 0);
  await card('贵金属持仓').getByText('黄金', { exact: true }).waitFor();
  await card('理财持仓').getByRole('button', { name: '添加持仓', exact: true }).click();
  await dialog.getByText('管理理财持仓', { exact: true }).waitFor();
  await dialog.getByPlaceholder('例如：银行理财产品').waitFor();
  await dialog.getByRole('button', { name: /取\s*消/ }).click();
  await dialog.waitFor({ state: 'hidden' });
  assert.equal(saves, 0);
  await card('基金持仓').getByRole('button', { name: '管理持仓' }).click();
  await dialog.getByRole('combobox').nth(0).click();
  await dialog.getByRole('combobox').nth(0).fill('混合');
  await page.locator('.ant-select-dropdown:visible').getByText('混合/宽基', { exact: true }).click();
  await dialog.getByRole('spinbutton').nth(0).fill('80');
  await dialog.getByRole('button', { name: '保存持仓' }).click();
  await dialog.getByText('同一资产类别的持仓占比合计不能超过100%').waitFor();
  assert.equal(saves, 0);
  await dialog.getByRole('spinbutton').nth(0).fill('66.67');
  await dialog.getByRole('spinbutton').nth(1).fill('33.33');
  await dialog.getByRole('button', { name: '保存持仓' }).click();
  await dialog.waitFor({ state: 'hidden' });
  assert.equal(saves, 1);
  assert.equal(holdings.find(item => item.categoryId === '1').sector, 'MIXED');
  await page.reload();
  await card('基金持仓').getByText('66.67%', { exact: true }).waitFor();
  await card('基金持仓').getByRole('button', { name: '管理持仓' }).click();
  await dialog.getByRole('button', { name: '删除第2项持仓' }).click();
  await dialog.getByRole('button', { name: '保存持仓' }).click();
  await dialog.waitFor({ state: 'hidden' });
  assert.equal(holdings.filter(item => item.categoryId === '1').length, 1);
  await card('加密货币持仓').getByRole('button', { name: '管理持仓' }).click();
  await dialog.getByRole('button', { name: '新增标的' }).click();
  await dialog.getByRole('textbox').nth(1).fill('btc');
  await dialog.getByRole('spinbutton').nth(1).fill('20');
  await dialog.getByRole('button', { name: '保存持仓' }).click();
  await dialog.getByText('同一类别的标的名称不能重复：btc').waitFor();
  await dialog.getByRole('textbox').nth(1).fill('ETH');
  await dialog.getByRole('button', { name: '保存持仓' }).click();
  await dialog.waitFor({ state: 'hidden' });
  assert.equal(holdings.filter(item => item.categoryId === '4').length, 2);
  for (const theme of ['dark', 'light']) {
    await page.evaluate(theme => localStorage.setItem('wealthflow_settings', JSON.stringify({ theme, themeColor: '#13a8a8' })), theme);
    await page.reload();
    await card('基金持仓').getByText('纳斯达克', { exact: true }).waitFor();
    await page.screenshot({ path: `node_modules/.cache/holdings-${theme}.png`, fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${theme}: 页面不能横向溢出`);
    await page.screenshot({ path: `node_modules/.cache/holdings-${theme}-mobile.png`, fullPage: true });
    await page.setViewportSize({ width: 1440, height: 1100 });
  }
  await card('贵金属持仓').getByRole('button', { name: '管理持仓' }).click();
  await dialog.getByText('管理贵金属持仓', { exact: true }).waitFor();
  await dialog.getByRole('button', { name: '删除第1项持仓' }).click();
  await dialog.getByText('保存后将清空该类别的具体持仓。').waitFor();
  await dialog.getByRole('button', { name: '保存持仓' }).click();
  await dialog.waitFor({ state: 'hidden' });
  assert.equal(holdings.filter(item => item.categoryId === '5').length, 0);
  await card('贵金属持仓').getByText('例如：黄金、白银', { exact: true }).waitFor();
  await card('贵金属持仓').getByRole('button', { name: '添加持仓', exact: true }).click();
  await dialog.getByRole('textbox').fill('白银');
  await dialog.getByRole('spinbutton').fill('25');
  await dialog.getByRole('button', { name: '保存持仓' }).click();
  await dialog.waitFor({ state: 'hidden' });
  assert.equal(holdings.find(item => item.categoryId === '5').name, '白银');
  assert.equal(holdings.filter(item => item.categoryId === '3').length, 0);
  assert.deepEqual(errors, []);
  console.log('持仓浏览器检查通过：六类标题与分类一致、理财和贵金属独立显示及编辑、菜单、保存重载、删除、超额与重名校验、深浅主题、窄屏布局、清空。');
} finally { await browser?.close(); await server.close(); }
