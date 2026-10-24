/**
 * 系统信息 Mock Service
 *
 * Mock 模式下数据存储在浏览器 localStorage，无真实数据库文件。
 */
import { categoryService as mockCategoryService } from './mockCategories';
import { snapshotService as mockSnapshotService } from './mockSnapshots';
import type { AssetBackupRestoreReport, SystemInfo, SystemService } from './types';
import { generateId } from './mockData';
import { today } from '../utils/date';
import { sumAmounts } from '../utils/amount';
import { pickUnusedCategoryColor } from '../utils/color';
import { parseBackupJson } from '../features/snapshots/importExport';
import { HOLDINGS_STORAGE_KEY, readMockHoldings } from './mockHoldings';

export const systemService: SystemService = {
  async getInfo(): Promise<SystemInfo> {
    const [categories, snapshots] = await Promise.all([
      mockCategoryService.getAll(),
      mockSnapshotService.getAll(),
    ]);
    return {
      dbPath: '浏览器 localStorage（Mock 模式）',
      categoryCount: categories.length,
      snapshotDateCount: snapshots.length,
    };
  },

  async clearAll(): Promise<void> {
    await mockSnapshotService.reset([]);
    localStorage.removeItem(HOLDINGS_STORAGE_KEY);
    await mockCategoryService.reset([]);
  },

  async restoreBackup(request): Promise<AssetBackupRestoreReport> {
    if (request.mode !== 'merge' && request.mode !== 'overwrite') throw new Error('恢复模式无效');
    const parsed = parseBackupJson(JSON.stringify(request));
    if (!parsed.ok) throw new Error(parsed.error);
    const categories = request.mode === 'merge' ? await mockCategoryService.getAll() : [];
    const snapshots = request.mode === 'merge' ? await mockSnapshotService.getAll() : [];
    const holdings = request.mode === 'merge' ? readMockHoldings() : [];
    const initialCategories = categories.length;
    const initialSnapshots = snapshots.length;
    const skipped: AssetBackupRestoreReport['skipped'] = [];
    const addCategory = (name: string, color?: string) => {
      let category = categories.find(item => item.name === name);
      if (!category) {
        category = { id: generateId(), name, color: color ?? pickUnusedCategoryColor(categories), createdAt: today() };
        categories.push(category);
      }
      return category;
    };
    parsed.backup.categories.forEach(category => {
      const target = addCategory(category.name, category.color);
      if (category.holdings && !holdings.some(item => item.categoryId === target.id)) {
        holdings.push(...category.holdings.map(item => ({ ...item, id: generateId(), categoryId: target.id })));
      }
    });
    for (const entry of parsed.backup.snapshots) {
      if (snapshots.some(snapshot => snapshot.snapshotDate === entry.snapshotDate)) {
        skipped.push({ date: entry.snapshotDate, reason: '数据库中已存在该日期的快照（合并模式不覆盖）' });
        continue;
      }
      const items = entry.items.map(item => ({ ...item, categoryId: addCategory(item.categoryName).id }));
      snapshots.push({ id: generateId(), snapshotDate: entry.snapshotDate, items,
        totalAmount: sumAmounts(items.map(item => item.amount)) });
    }
    // 所有校验与计算完成后写入；第二次写入失败时恢复第一次写入。
    const previousCategories = localStorage.getItem('wealthflow_categories');
    const previousHoldings = localStorage.getItem(HOLDINGS_STORAGE_KEY);
    localStorage.setItem('wealthflow_categories', JSON.stringify(categories));
    try {
      localStorage.setItem(HOLDINGS_STORAGE_KEY, JSON.stringify(holdings));
      localStorage.setItem('wealthflow_snapshots', JSON.stringify(snapshots));
    } catch (error) {
      if (previousCategories === null) localStorage.removeItem('wealthflow_categories');
      else localStorage.setItem('wealthflow_categories', previousCategories);
      if (previousHoldings === null) localStorage.removeItem(HOLDINGS_STORAGE_KEY);
      else localStorage.setItem(HOLDINGS_STORAGE_KEY, previousHoldings);
      throw error;
    }
    return { createdSnapshots: snapshots.length - initialSnapshots,
      createdCategories: categories.length - initialCategories, skipped };
  },
};
