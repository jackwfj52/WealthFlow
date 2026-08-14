/**
 * 分类真实 API Service（Spring Boot）
 * 与 Mock 实现保持相同的 CategoryService 接口签名。
 */
import type { AssetCategory } from '../types/domain';
import { ApiError, apiClient } from './apiClient';
import type { CategoryService } from './types';

interface CategoryPayload {
  id: string;
  name: string;
  createdAt: string | null;
}

function toDomain(category: CategoryPayload): AssetCategory {
  return { id: category.id, name: category.name, createdAt: category.createdAt ?? '' };
}

export const apiCategoryService: CategoryService = {
  async getAll(): Promise<AssetCategory[]> {
    const list = await apiClient.get<CategoryPayload[]>('/categories');
    return list.map(toDomain);
  },

  async getById(id: string): Promise<AssetCategory | undefined> {
    if (!id) return undefined;
    try {
      return toDomain(await apiClient.get<CategoryPayload>(`/categories/${id}`));
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) return undefined;
      throw err;
    }
  },

  async create(name: string): Promise<AssetCategory> {
    return toDomain(await apiClient.post<CategoryPayload>('/categories', { name }));
  },

  async update(id: string, name: string): Promise<AssetCategory | undefined> {
    if (!id) return undefined;
    try {
      return toDomain(await apiClient.patch<CategoryPayload>(`/categories/${id}`, { name }));
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) return undefined;
      throw err;
    }
  },

  async delete(id: string): Promise<boolean> {
    if (!id) return false;
    try {
      await apiClient.delete(`/categories/${id}`);
      return true;
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) return false;
      throw err;
    }
  },

  async reset(): Promise<void> {
    throw new Error('真实 API 模式不支持重置示例数据');
  },
};
