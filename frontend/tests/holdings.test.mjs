import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateHoldings, totalPercentage } from '../src/utils/holdings.ts';
import { buildBackupJson, parseBackupJson, runBackupRestore } from '../src/features/snapshots/importExport.ts';

test('持仓百分比按基点合计，允许未登记部分并拒绝超额、重名和无效数据', () => {
  assert.equal(totalPercentage(validateHoldings([{ name: ' 纳斯达克 ', percentage: 33.33 }, { name: '标普500', percentage: 66.67 }])), 100);
  assert.deepEqual(validateHoldings([]), []);
  for (const items of [
    [{ name: 'A', percentage: 60 }, { name: 'B', percentage: 40.01 }],
    [{ name: 'BTC', percentage: 10 }, { name: ' btc ', percentage: 10 }],
    [{ name: ' ', percentage: 10 }], [{ name: 'A', percentage: 0 }],
    [{ name: 'A', percentage: NaN }], [{ name: 'A', percentage: 0.001 }],
  ]) assert.throws(() => validateHoldings(items));
});

test('含持仓的新版备份可往返，旧版仍可解析，无效持仓阻止恢复', async () => {
  const categories = [{ name: '基金', color: '#123456', holdings: [{ name: '纳斯达克', percentage: 60 }] }];
  const exported = buildBackupJson(categories, []);
  const parsed = parseBackupJson(exported);
  assert.equal(parsed.ok, true);
  assert.equal(parsed.backup.version, 2);
  assert.deepEqual(parsed.backup.categories, categories);
  let received;
  await runBackupRestore(parsed.backup, 'overwrite', { async restoreBackup(request) { received = request; return {}; } });
  assert.deepEqual(received.categories, categories);
  const invalid = JSON.parse(exported);
  invalid.categories[0].holdings[0].percentage = 101;
  assert.equal(parseBackupJson(JSON.stringify(invalid)).ok, false);
  assert.equal(parseBackupJson(JSON.stringify({ version: 1, categories: [{ name: '基金' }], snapshots: [] })).ok, true);
});

test('行业标签可选且随新版备份保留，不根据标的名称猜行业', () => {
  assert.deepEqual(validateHoldings([{ name: '英伟达', percentage: 60 }]), [{ name: '英伟达', percentage: 60 }]);
  const holdings = [{ name: '英伟达', percentage: 60, sector: 'TECH' }];
  assert.deepEqual(validateHoldings(holdings), holdings);
  assert.throws(() => validateHoldings([{ ...holdings[0], sector: 'invalid' }]));
  const exported = buildBackupJson([{ name: '股票', holdings }], []);
  const parsed = parseBackupJson(exported);
  assert.equal(parsed.ok, true);
  assert.equal(parsed.backup.version, 3);
  assert.deepEqual(parsed.backup.categories[0].holdings, holdings);
});
