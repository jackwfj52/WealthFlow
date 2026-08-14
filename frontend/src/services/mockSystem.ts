/**
 * 系统信息 Mock Service
 *
 * Mock 模式下数据存储在浏览器 localStorage，无真实数据库文件。
 */
import { categoryService as mockCategoryService } from './mockCategories';
import { snapshotService as mockSnapshotService } from './mockSnapshots';
import type { SystemInfo, SystemService } from './types';

export const systemService: SystemService = {
  async getInfo(): Promise<SystemInfo> {
    const [categories, snapshots] = await Promise.all([
      mockCategoryService.getAll(),
      mockSnapshotService.getAll(),
    ]);
    return {
      dbPath: '浏览器 localStorage（Mock 模式）',
      categoryCount: categories.length,
      snapshotRowCount: snapshots.reduce(
        (acc: number, s) => acc + s.items.length,
        0,
      ),
    };
  },

  async clearAll(): Promise<void> {
    await mockSnapshotService.reset([]);
    await mockCategoryService.reset([]);
  },
};
