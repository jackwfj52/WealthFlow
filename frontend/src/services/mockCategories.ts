/**
 * 分类 Mock Service
 *
 * 使用 localStorage 持久化分类数据。
 * 后续对接 Spring Boot 时：替换为本文件内的函数实现为 fetch/axios 调用即可，
 * 组件层无需任何修改。
 */
import type { AssetCategory, AssetSnapshot } from '../types/domain';
import { generateId } from './mockData';
import { today } from '../utils/date';

const STORAGE_KEY = 'wealthflow_categories';
const SNAPSHOT_KEY = 'wealthflow_snapshots';

function read(): AssetCategory[] {
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

function write(categories: AssetCategory[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(categories));
}

function readSnapshots(): AssetSnapshot[] {
  try {
    const raw = localStorage.getItem(SNAPSHOT_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed;
  } catch {
    return [];
  }
}

function writeSnapshots(snapshots: AssetSnapshot[]): void {
  localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshots));
}

function validateName(name: unknown, categories: AssetCategory[], excludeId?: string): string {
  if (typeof name !== 'string') {
    throw new Error('分类名称必须为字符串');
  }
  const trimmed = name.trim();
  if (!trimmed) {
    throw new Error('分类名称不能为空或纯空格');
  }
  if (trimmed.length > 20) {
    throw new Error('分类名称不超过20个字符');
  }
  const duplicate = categories.find(
    (c) => c.name === trimmed && c.id !== excludeId
  );
  if (duplicate) {
    throw new Error(`分类名称"${trimmed}"已存在`);
  }
  return trimmed;
}

export const categoryService = {
  /** 获取所有分类 */
  getAll(): AssetCategory[] {
    return read();
  },

  /** 按 ID 获取 */
  getById(id: string): AssetCategory | undefined {
    if (!id) return undefined;
    return read().find((c) => c.id === id);
  },

  /**
   * 新增分类
   * @throws 名称重复、空名称、纯空格时抛出错误
   */
  create(name: string): AssetCategory {
    const categories = read();
    const validName = validateName(name, categories);
    const category: AssetCategory = {
      id: generateId(),
      name: validName,
      createdAt: today(),
    };
    categories.push(category);
    write(categories);
    return category;
  },

  /**
   * 编辑分类名称
   *
   * 重命名时会同步更新所有历史快照中匹配的分类名称，
   * 确保历史数据显示的是最新分类名。
   *
   * @throws 名称重复、空名称、纯空格时抛出错误
   */
  update(id: string, name: string): AssetCategory | undefined {
    if (!id) return undefined;
    const categories = read();
    const idx = categories.findIndex((c) => c.id === id);
    if (idx === -1) return undefined;

    const oldName = categories[idx].name;
    const validName = validateName(name, categories, id);

    categories[idx] = { ...categories[idx], name: validName };
    write(categories);

    // 同步更新所有快照中的 categoryName
    if (oldName !== validName) {
      const snapshots = readSnapshots();
      let changed = false;
      for (const s of snapshots) {
        for (const item of s.items) {
          if (item.categoryId === id) {
            item.categoryName = validName;
            changed = true;
          }
        }
      }
      if (changed) {
        writeSnapshots(snapshots);
      }
    }

    return categories[idx];
  },

  /** 删除分类 */
  delete(id: string): boolean {
    if (!id) return false;
    const categories = read();
    const filtered = categories.filter((c) => c.id !== id);
    if (filtered.length === categories.length) return false;
    write(filtered);
    return true;
  },

  /** 批量重置为初始数据 */
  reset(categories: AssetCategory[]): void {
    if (!Array.isArray(categories)) return;
    write(categories);
  },
};
