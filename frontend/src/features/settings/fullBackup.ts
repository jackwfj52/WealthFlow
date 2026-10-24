import { normalizeSettings, type AppSettings } from '../../app/settingsValues';

export interface FullBackup {
  type: 'wealthflow-full-backup';
  version: 1;
  exportedAt: string;
  tables: Record<string, Record<string, unknown>[]>;
  settings: AppSettings;
}

export function parseFullBackup(text: string): FullBackup {
  const value = JSON.parse(text) as Partial<FullBackup>;
  if (value?.type !== 'wealthflow-full-backup' || value.version !== 1
    || !value.tables || Array.isArray(value.tables) || typeof value.tables !== 'object'
    || !value.settings || typeof value.settings !== 'object') {
    throw new Error('请选择 WealthFlow 完整备份文件，资产备份请使用原有导入入口');
  }
  for (const rows of Object.values(value.tables)) {
    if (!Array.isArray(rows) || rows.some(row => !row || typeof row !== 'object' || Array.isArray(row))) {
      throw new Error('完整备份数据格式错误');
    }
  }
  return { ...value, settings: normalizeSettings(value.settings) } as FullBackup;
}
