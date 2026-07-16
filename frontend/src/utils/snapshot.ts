import type { AssetSnapshot, SnapshotItem } from '../types/domain';
import { getWeekStart, getMonthStart } from './date';

/** 根据快照数组计算总资产（取最新快照的总金额） */
export function getTotalAssets(snapshots: AssetSnapshot[]): string {
  if (snapshots.length === 0) return '0.00';
  const sorted = [...snapshots].sort((a, b) => b.snapshotDate.localeCompare(a.snapshotDate));
  return sorted[0].totalAmount || '0.00';
}

/** 获取指定日期的快照 */
export function getSnapshotByDate(
  snapshots: AssetSnapshot[],
  date: string
): AssetSnapshot | undefined {
  return snapshots.find((s) => s.snapshotDate === date);
}

/** 按分类汇总金额（基于最新快照） */
export function getCategoryBreakdown(
  snapshots: AssetSnapshot[]
): { categoryId: string; categoryName: string; amount: string }[] {
  if (snapshots.length === 0) return [];
  const sorted = [...snapshots].sort((a, b) => b.snapshotDate.localeCompare(a.snapshotDate));
  return sorted[0].items.map((item) => ({
    categoryId: item.categoryId,
    categoryName: item.categoryName,
    amount: item.amount,
  }));
}

/** 计算分类占比 */
export function getCategoryPercentages(
  items: SnapshotItem[]
): { categoryId: string; categoryName: string; amount: string; percent: number }[] {
  const totalCents = items.reduce((acc, i) => acc + Math.round(parseFloat(i.amount || '0') * 100), 0);
  if (totalCents === 0) return items.map((i) => ({ ...i, percent: 0 }));
  return items.map((i) => {
    const itemCents = Math.round(parseFloat(i.amount || '0') * 100);
    return {
      categoryId: i.categoryId,
      categoryName: i.categoryName,
      amount: i.amount,
      percent: parseFloat(((itemCents / totalCents) * 100).toFixed(1)),
    };
  });
}

/** 趋势数据：按日期返回总资产 */
export function getTrendData(
  snapshots: AssetSnapshot[],
  startDate: string,
  endDate: string
): { date: string; totalAmount: string }[] {
  const range = snapshots
    .filter((s) => s.snapshotDate >= startDate && s.snapshotDate <= endDate)
    .sort((a, b) => a.snapshotDate.localeCompare(b.snapshotDate));
  return range.map((s) => ({
    date: s.snapshotDate,
    totalAmount: s.totalAmount,
  }));
}

/** 趋势数据按分类：返回每个分类的逐日金额 */
export function getCategoryTrendData(
  snapshots: AssetSnapshot[],
  categoryId: string,
  startDate: string,
  endDate: string
): { date: string; amount: string }[] {
  const range = snapshots
    .filter((s) => s.snapshotDate >= startDate && s.snapshotDate <= endDate)
    .sort((a, b) => a.snapshotDate.localeCompare(b.snapshotDate));
  return range.map((s) => {
    const item = s.items.find((i) => i.categoryId === categoryId);
    return { date: s.snapshotDate, amount: item?.amount ?? '0' };
  });
}

/** 按聚合粒度汇总趋势 */
export type Aggregation = 'day' | 'week' | 'month';

/**
 * 聚合趋势数据
 *
 * 日模式：直接返回，不做聚合
 * 周/月模式：将同一周期内的数据归为一组，取该周期内最新日期的值，
 * 而不是累加。因为趋势展示的是"该周期末的资产状态"，而非"周期内总和"。
 */
export function aggregateTrendData(
  data: { date: string; totalAmount: string }[],
  aggregation: Aggregation
): { label: string; totalAmount: string }[] {
  if (data.length === 0) return [];

  if (aggregation === 'day') {
    return data.map((d) => ({ label: d.date, totalAmount: d.totalAmount }));
  }

  // 按周期分组，每组保留"日期最晚"的那条（代表该周期末的资产状态）
  const groupLatest = new Map<string, { date: string; totalAmount: string }>();

  data.forEach((d) => {
    const key = aggregation === 'week' ? getWeekStart(d.date) : getMonthStart(d.date);
    const existing = groupLatest.get(key);
    if (!existing || d.date.localeCompare(existing.date) > 0) {
      groupLatest.set(key, d);
    }
  });

  return Array.from(groupLatest.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, entry]) => ({
      label: key,
      totalAmount: entry.totalAmount,
    }));
}

/** 按日期和分类检查重复 */
export function findDuplicateItem(
  items: SnapshotItem[],
  categoryId: string
): SnapshotItem | undefined {
  return items.find((i) => i.categoryId === categoryId);
}
