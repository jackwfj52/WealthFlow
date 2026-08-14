/**
 * Service 工厂
 *
 * 根据环境变量 VITE_USE_MOCK 决定数据来源：
 * - 'false' → 真实 Spring Boot API（/api/v1，经 Vite 代理）
 * - 其他值或未设置 → Mock（localStorage）
 *
 * 页面组件只从本文件导入 service，不感知数据来源。
 */
import type { CategoryService, SnapshotService } from './types';
import { categoryService as syncMockCategories } from './mockCategories';
import { snapshotService as syncMockSnapshots } from './mockSnapshots';
import { apiCategoryService } from './apiCategories';
import { apiSnapshotService } from './apiSnapshots';

/** 当前是否处于 Mock 模式 */
export const USE_MOCK: boolean = import.meta.env.VITE_USE_MOCK !== 'false';

/**
 * Mock service 内部仍为同步实现，这里统一转换为异步签名，
 * 使两种实现对外暴露完全一致的接口。
 */
const mockCategoryService: CategoryService = {
  async getAll() {
    return syncMockCategories.getAll();
  },
  async getById(id: string) {
    return syncMockCategories.getById(id);
  },
  async create(name: string) {
    return syncMockCategories.create(name);
  },
  async update(id: string, name: string) {
    return syncMockCategories.update(id, name);
  },
  async delete(id: string) {
    return syncMockCategories.delete(id);
  },
  async reset(categories) {
    syncMockCategories.reset(categories);
  },
};

const mockSnapshotService: SnapshotService = {
  async getAll() {
    return syncMockSnapshots.getAll();
  },
  async getById(id: string) {
    return syncMockSnapshots.getById(id);
  },
  async getByDate(date: string) {
    return syncMockSnapshots.getByDate(date);
  },
  async existsByDate(date: string) {
    return syncMockSnapshots.existsByDate(date);
  },
  async create(snapshotDate: string, items) {
    return syncMockSnapshots.create(snapshotDate, items);
  },
  async update(id: string, items) {
    return syncMockSnapshots.update(id, items);
  },
  async delete(id: string) {
    return syncMockSnapshots.delete(id);
  },
  async filterByDateRange(startDate: string, endDate: string) {
    return syncMockSnapshots.filterByDateRange(startDate, endDate);
  },
  async reset(snapshots) {
    syncMockSnapshots.reset(snapshots);
  },
};

export const categoryService: CategoryService = USE_MOCK
  ? mockCategoryService
  : apiCategoryService;

export const snapshotService: SnapshotService = USE_MOCK
  ? mockSnapshotService
  : apiSnapshotService;
