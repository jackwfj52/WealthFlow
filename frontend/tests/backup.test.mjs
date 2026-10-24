import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseBackupJson, runBackupRestore } from '../src/features/snapshots/importExport.ts';
import { systemService as mockSystem } from '../src/services/mockSystem.ts';

const backup = () => ({ type: 'wealthflow-backup', version: 1, categories: [{ name: '现金' }],
  snapshots: [{ snapshotDate: '2026-01-01', items: [{ categoryName: '现金', amount: '123.45' }] }] });

test('超长显式分类、隐式分类和无效颜色在恢复前被拒绝', () => {
  for (const mutate of [
    data => { data.categories[0].name = 'a'.repeat(21); },
    data => { data.snapshots[0].items[0].categoryName = 'a'.repeat(21); },
    data => { data.categories[0].color = 'invalid'; },
  ]) {
    const data = backup(); mutate(data);
    assert.equal(parseBackupJson(JSON.stringify(data)).ok, false);
  }
});

test('备份含无效快照时整份拒绝，不允许覆盖恢复时静默跳过', () => {
  const data = backup();
  data.snapshots.push({ snapshotDate: 'invalid', items: [] });
  assert.equal(parseBackupJson(JSON.stringify(data)).ok, false);
});

test('有效恢复只发出一次事务请求，不先清空数据', async () => {
  let calls = 0;
  const system = {
    clearAll() { assert.fail('不得先清空数据'); },
    async restoreBackup(request) {
      calls++;
      assert.equal(request.mode, 'overwrite');
      assert.deepEqual(request.snapshots, backup().snapshots);
      return { createdCategories: 1, createdSnapshots: 1, skipped: [] };
    },
  };
  const report = await runBackupRestore(backup(), 'overwrite', system);
  assert.equal(calls, 1); assert.equal(report.createdSnapshots, 1);
  const invalid = backup(); invalid.categories[0].name = 'a'.repeat(21);
  await assert.rejects(runBackupRestore(invalid, 'overwrite', system));
  assert.equal(calls, 1);
});

test('后端恢复失败原样报告，不回退到逐条清空和导入', async () => {
  const failure = new Error('事务回滚');
  await assert.rejects(runBackupRestore(backup(), 'overwrite', {
    clearAll() { assert.fail('不得清空'); },
    restoreBackup() { throw failure; },
  }), error => error === failure);
});

test('Mock恢复写入失败时保留原有分类与快照', async () => {
  const originalCategories = '[{"id":"old","name":"原有","createdAt":"2026-01-01"}]';
  const originalSnapshots = '[{"id":"old","snapshotDate":"2026-01-01","items":[],"totalAmount":"1"}]';
  const storage = new Map([['wealthflow_categories', originalCategories], ['wealthflow_snapshots', originalSnapshots]]);
  globalThis.localStorage = {
    getItem: key => storage.get(key) ?? null,
    setItem(key, value) {
      if (key === 'wealthflow_snapshots') throw new Error('模拟空间不足');
      storage.set(key, value);
    },
    removeItem: key => storage.delete(key),
  };
  await assert.rejects(mockSystem.restoreBackup({ ...backup(), mode: 'overwrite' }));
  assert.equal(storage.get('wealthflow_categories'), originalCategories);
  assert.equal(storage.get('wealthflow_snapshots'), originalSnapshots);
  delete globalThis.localStorage;
});
