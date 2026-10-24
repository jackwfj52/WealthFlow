import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseFullBackup } from '../src/features/settings/fullBackup.ts';
import { DEFAULT_SETTINGS } from '../src/app/settingsValues.ts';

test('完整备份保留界面设置，隔离未知字段并清洗异常偏好', () => {
  const input = { type: 'wealthflow-full-backup', version: 1, tables: { asset_category: [] },
    settings: { ...DEFAULT_SETTINGS, theme: 'dark', themeColor: '#13c2c2', defaultTrendDays: -20, unknown: 'drop' } };
  const result = parseFullBackup(JSON.stringify(input));
  assert.equal(result.settings.theme, 'dark');
  assert.equal(result.settings.themeColor, '#13c2c2');
  assert.equal(result.settings.defaultTrendDays, DEFAULT_SETTINGS.defaultTrendDays);
  assert.equal(result.settings.unknown, undefined);
});

test('错误版本、资产备份和损坏分区不可作为完整备份导入', () => {
  for (const value of [null, {}, { type: 'wealthflow-backup', version: 3 },
    { type: 'wealthflow-full-backup', version: 1, settings: {}, tables: { asset_category: 'broken' } }]) {
    assert.throws(() => parseFullBackup(JSON.stringify(value)));
  }
});
