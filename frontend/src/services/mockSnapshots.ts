/**
 * 快照 Mock Service
 *
 * 核心规则（必须严格遵守）：
 * 1. 每日资产更新按"新增快照"处理，不能直接覆盖历史资产数据
 * 2. 一个快照对应一个 snapshotDate，包含该日期下多个资产分类的金额明细
 * 3. 新增日期没有快照时 → 创建一组新的快照数据
 * 4. 同一日期已有快照时 → 提示用户选择"编辑当天快照"或"取消"，不能静默覆盖
 * 5. 编辑快照时只能修改指定日期的数据，不能影响其他日期
 * 6. 同一日期同一分类最多只能有一条明细
 * 7. 删除快照需要二次确认
 * 8. 总资产、分类占比和趋势数据必须根据快照实时计算
 *
 * 后续对接 Spring Boot 时：替换为本文件内的函数实现为 fetch/axios 调用即可，
 * 组件层无需任何修改。
 */
import type { AssetSnapshot, SnapshotItem } from '../types/domain';
import { generateId } from './mockData';
import { sumAmounts } from '../utils/amount';
import {
  isValidDateOnly,
  isValidDateFormat,
  isValidDateRange,
} from '../utils/date';

const STORAGE_KEY = 'wealthflow_snapshots';

function read(): AssetSnapshot[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed;
  } catch {
    return [];
  }
}

function write(snapshots: AssetSnapshot[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshots));
}

/** 校验单个 SnapshotItem */
function validateItem(item: unknown): item is SnapshotItem {
  if (!item || typeof item !== 'object') return false;
  const i = item as Record<string, unknown>;
  if (typeof i.categoryId !== 'string' || !i.categoryId) return false;
  if (typeof i.categoryName !== 'string') return false;
  if (typeof i.amount !== 'string' || !i.amount) return false;
  if (!/^\d+(\.\d{1,2})?$/.test(i.amount)) return false;
  return true;
}

/** 校验 items 数组，过滤无效项，检查分类重复 */
function validateItems(items: unknown): { valid: SnapshotItem[]; error?: string } {
  if (!Array.isArray(items) || items.length === 0) {
    return { valid: [], error: '快照明细不能为空' };
  }

  const valid = items.filter(validateItem);
  if (valid.length === 0) {
    return { valid: [], error: '至少需要一个有效的分类金额' };
  }

  const seen = new Set<string>();
  for (const item of valid) {
    if (seen.has(item.categoryId)) {
      return { valid: [], error: `分类"${item.categoryName}"重复` };
    }
    seen.add(item.categoryId);
  }

  return { valid };
}

export const snapshotService = {
  /** 获取所有快照 */
  getAll(): AssetSnapshot[] {
    return read();
  },

  /** 按 ID 获取 */
  getById(id: string): AssetSnapshot | undefined {
    if (!id) return undefined;
    return read().find((s) => s.id === id);
  },

  /**
   * 按日期获取快照。
   * 日期非法时返回 undefined（不抛异常，适合查询场景）。
   */
  getByDate(date: string): AssetSnapshot | undefined {
    if (!isValidDateFormat(date)) return undefined;
    return read().find((s) => s.snapshotDate === date);
  },

  /**
   * 检查指定日期是否已有快照。
   * 日期非法时返回 false。
   */
  existsByDate(date: string): boolean {
    if (!isValidDateFormat(date)) return false;
    return read().some((s) => s.snapshotDate === date);
  },

  /**
   * 新增快照
   *
   * 日期必须符合 YYYY-MM-DD 真实日期且不能晚于今天。
   * 若日期已存在快照则抛出错误（调用方应先 checked existsByDate 并引导编辑）。
   */
  create(snapshotDate: string, items: SnapshotItem[]): AssetSnapshot {
    if (!isValidDateOnly(snapshotDate)) {
      if (typeof snapshotDate !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(snapshotDate)) {
        throw new Error('日期格式无效，请输入真实日期（YYYY-MM-DD），例如 2026-07-16');
      }
      throw new Error(
        `日期"${snapshotDate}"无效。请确认该日期真实存在且不晚于今天。`
      );
    }

    const snapshots = read();
    if (snapshots.some((s) => s.snapshotDate === snapshotDate)) {
      throw new Error(`日期 ${snapshotDate} 已有快照，请使用编辑功能而非新增`);
    }

    const { valid, error } = validateItems(items);
    if (error) throw new Error(error);

    const snapshot: AssetSnapshot = {
      id: generateId(),
      snapshotDate,
      items: valid,
      totalAmount: sumAmounts(valid.map((i) => i.amount)),
    };
    snapshots.push(snapshot);
    write(snapshots);
    return snapshot;
  },

  /**
   * 编辑快照
   *
   * 只能修改指定日期的数据，不会影响其他日期。
   */
  update(id: string, items: SnapshotItem[]): AssetSnapshot | undefined {
    if (!id) return undefined;

    const snapshots = read();
    const idx = snapshots.findIndex((s) => s.id === id);
    if (idx === -1) return undefined;

    const { valid, error } = validateItems(items);
    if (error) throw new Error(error);

    snapshots[idx] = {
      ...snapshots[idx],
      items: valid,
      totalAmount: sumAmounts(valid.map((i) => i.amount)),
    };
    write(snapshots);
    return snapshots[idx];
  },

  /** 删除快照 */
  delete(id: string): boolean {
    if (!id) return false;
    const snapshots = read();
    const filtered = snapshots.filter((s) => s.id !== id);
    if (filtered.length === snapshots.length) return false;
    write(filtered);
    return true;
  },

  /**
   * 按日期范围筛选。
   * 两个日期都必须合法，且开始 ≤ 结束，否则返回空数组。
   */
  filterByDateRange(startDate: string, endDate: string): AssetSnapshot[] {
    const range = isValidDateRange(startDate, endDate);
    if (!range.valid) return [];

    return read()
      .filter((s) => s.snapshotDate >= startDate && s.snapshotDate <= endDate)
      .sort((a, b) => b.snapshotDate.localeCompare(a.snapshotDate));
  },

  /** 批量重置 */
  reset(snapshots: AssetSnapshot[]): void {
    if (!Array.isArray(snapshots)) return;
    write(snapshots);
  },
};
