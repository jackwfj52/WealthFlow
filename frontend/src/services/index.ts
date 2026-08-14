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
import { categoryService as mockCategoryService } from './mockCategories';
import { snapshotService as mockSnapshotService } from './mockSnapshots';
import { apiCategoryService } from './apiCategories';
import { apiSnapshotService } from './apiSnapshots';

/** 当前是否处于 Mock 模式 */
export const USE_MOCK: boolean = import.meta.env.VITE_USE_MOCK !== 'false';

export const categoryService: CategoryService = USE_MOCK
  ? mockCategoryService
  : apiCategoryService;

export const snapshotService: SnapshotService = USE_MOCK
  ? mockSnapshotService
  : apiSnapshotService;
