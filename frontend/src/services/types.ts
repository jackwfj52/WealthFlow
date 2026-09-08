/**
 * 服务层统一接口
 *
 * Mock 实现与真实 API 实现保持完全相同的异步签名，
 * 页面组件只依赖接口，不感知数据来源。
 */
import type { AssetCategory, AssetSnapshot, SnapshotItem } from '../types/domain';

export interface CategoryService {
  getAll(): Promise<AssetCategory[]>;
  getById(id: string): Promise<AssetCategory | undefined>;
  create(name: string, color?: string): Promise<AssetCategory>;
  update(id: string, name: string, color?: string): Promise<AssetCategory | undefined>;
  delete(id: string): Promise<boolean>;
  /** 重置数据（仅 Mock 模式支持） */
  reset(categories: AssetCategory[]): Promise<void>;
}

export interface SnapshotService {
  getAll(): Promise<AssetSnapshot[]>;
  getById(id: string): Promise<AssetSnapshot | undefined>;
  getByDate(date: string): Promise<AssetSnapshot | undefined>;
  existsByDate(date: string): Promise<boolean>;
  create(snapshotDate: string, items: SnapshotItem[]): Promise<AssetSnapshot>;
  update(id: string, items: SnapshotItem[]): Promise<AssetSnapshot | undefined>;
  delete(id: string): Promise<boolean>;
  filterByDateRange(startDate: string, endDate: string): Promise<AssetSnapshot[]>;
  /** 重置数据（仅 Mock 模式支持） */
  reset(snapshots: AssetSnapshot[]): Promise<void>;
}

/** 系统信息（数据库路径与数据量统计） */
export interface SystemInfo {
  dbPath: string;
  categoryCount: number;
  snapshotRowCount: number;
}

export interface SystemService {
  getInfo(): Promise<SystemInfo>;
  /** 清空全部数据（分类 + 快照），不可恢复 */
  clearAll(): Promise<void>;
}
