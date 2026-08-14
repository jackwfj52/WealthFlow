/**
 * Service 工厂
 *
 * 数据默认存储在后端数据库，走真实 Spring Boot API（/api/v1，经 Vite 代理）。
 * 仅当显式设置 VITE_USE_MOCK='true' 时才使用 localStorage Mock（调试用）。
 *
 * 页面组件只从本文件导入 service，不感知数据来源。
 */
import type { CategoryService, SnapshotService, SystemService } from './types';
import { categoryService as mockCategoryService } from './mockCategories';
import { snapshotService as mockSnapshotService } from './mockSnapshots';
import { systemService as mockSystemService } from './mockSystem';
import { apiCategoryService } from './apiCategories';
import { apiSnapshotService } from './apiSnapshots';
import { apiSystemService } from './apiSystem';

/** 当前是否处于 Mock 模式（仅显式开启，默认走真实 API） */
export const USE_MOCK: boolean = import.meta.env.VITE_USE_MOCK === 'true';

export const categoryService: CategoryService = USE_MOCK
  ? mockCategoryService
  : apiCategoryService;

export const snapshotService: SnapshotService = USE_MOCK
  ? mockSnapshotService
  : apiSnapshotService;

export const systemService: SystemService = USE_MOCK
  ? mockSystemService
  : apiSystemService;
