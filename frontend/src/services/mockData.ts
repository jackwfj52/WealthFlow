import type { AssetCategory, AssetSnapshot } from '../types/domain';
import { sumAmounts } from '../utils/amount';
import { daysAgo, today } from '../utils/date';

/** 生成唯一 ID */
export function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).substring(2, 9);
}

/** 初始分类模板 */
export const SEED_CATEGORIES: AssetCategory[] = [
  { id: 'cat-cash', name: '现金', createdAt: '2026-01-01' },
  { id: 'cat-deposit', name: '存款', createdAt: '2026-01-01' },
  { id: 'cat-stock', name: '股票', createdAt: '2026-01-01' },
  { id: 'cat-fund', name: '基金', createdAt: '2026-01-01' },
  { id: 'cat-wealth', name: '理财', createdAt: '2026-01-01' },
  { id: 'cat-insurance', name: '保险', createdAt: '2026-01-01' },
  { id: 'cat-house', name: '房产', createdAt: '2026-01-01' },
  { id: 'cat-other', name: '其他', createdAt: '2026-01-01' },
];

/**
 * 生成示例快照数据
 * 创建多个日期的快照，确保趋势图有足够数据展示
 */
function buildSeedSnapshots(): AssetSnapshot[] {
  const cats = SEED_CATEGORIES;
  // 模拟最近 90 天内若干天的数据
  const dates = [
    { dayOffset: 90, config: [5, 80, 45, 30, 15, 8, 200, 3].map(String) },
    { dayOffset: 75, config: [5.2, 82, 48, 30.5, 15, 8, 210, 3.5].map(String) },
    { dayOffset: 60, config: [4.8, 85, 47, 31, 14.5, 7.5, 205, 3.2].map(String) },
    { dayOffset: 45, config: [5, 88, 50, 33, 16, 9, 220, 4].map(String) },
    { dayOffset: 30, config: [5.5, 90, 45, 34, 16, 9, 215, 4].map(String) },
    { dayOffset: 14, config: [5.3, 92, 48, 35, 16.5, 9.2, 218, 4.3].map(String) },
    { dayOffset: 7, config: [5.8, 95, 49, 36, 17, 9.5, 222, 4.5].map(String) },
    { dayOffset: 0, config: [6, 100, 50, 38, 18, 10, 230, 5].map(String) },
  ];

  return dates.map(({ dayOffset, config }) => {
    const dateStr = dayOffset === 0 ? today() : daysAgo(dayOffset);
    const items = cats.map((cat, idx) => ({
      categoryId: cat.id,
      categoryName: cat.name,
      amount: config[idx] || '0',
    }));
    return {
      id: generateId(),
      snapshotDate: dateStr,
      items,
      totalAmount: sumAmounts(items.map((i) => i.amount)),
    };
  });
}

export const SEED_SNAPSHOTS = buildSeedSnapshots();
