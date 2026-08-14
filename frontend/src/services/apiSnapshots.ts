/**
 * 快照真实 API Service（Spring Boot）
 * 与 Mock 实现保持相同的 SnapshotService 接口签名。
 */
import type { AssetSnapshot, SnapshotItem } from '../types/domain';
import { ApiError, apiClient } from './apiClient';
import type { SnapshotService } from './types';

interface SnapshotPayload {
  id: string;
  snapshotDate: string;
  items: SnapshotItem[];
  totalAmount: string;
}

/** 后端通过 categoryId 关联分类名称，请求时无需上传 categoryName */
function toRequestItems(items: SnapshotItem[]): { categoryId: string; amount: string }[] {
  return items.map((item) => ({ categoryId: item.categoryId, amount: item.amount }));
}

export const apiSnapshotService: SnapshotService = {
  async getAll(): Promise<AssetSnapshot[]> {
    return apiClient.get<SnapshotPayload[]>('/snapshots');
  },

  async getById(id: string): Promise<AssetSnapshot | undefined> {
    if (!id) return undefined;
    try {
      return await apiClient.get<SnapshotPayload>(`/snapshots/${id}`);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) return undefined;
      throw err;
    }
  },

  async getByDate(date: string): Promise<AssetSnapshot | undefined> {
    const all = await apiClient.get<SnapshotPayload[]>('/snapshots');
    return all.find((s) => s.snapshotDate === date);
  },

  async existsByDate(date: string): Promise<boolean> {
    const all = await apiClient.get<SnapshotPayload[]>('/snapshots');
    return all.some((s) => s.snapshotDate === date);
  },

  async create(snapshotDate: string, items: SnapshotItem[]): Promise<AssetSnapshot> {
    return apiClient.post<SnapshotPayload>('/snapshots', {
      snapshotDate,
      items: toRequestItems(items),
    });
  },

  async update(id: string, items: SnapshotItem[]): Promise<AssetSnapshot | undefined> {
    if (!id) return undefined;
    try {
      return await apiClient.put<SnapshotPayload>(`/snapshots/${id}`, {
        items: toRequestItems(items),
      });
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) return undefined;
      throw err;
    }
  },

  async delete(id: string): Promise<boolean> {
    if (!id) return false;
    try {
      await apiClient.delete(`/snapshots/${id}`);
      return true;
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) return false;
      throw err;
    }
  },

  async filterByDateRange(startDate: string, endDate: string): Promise<AssetSnapshot[]> {
    const all = await apiClient.get<SnapshotPayload[]>('/snapshots');
    return all
      .filter((s) => s.snapshotDate >= startDate && s.snapshotDate <= endDate)
      .sort((a, b) => b.snapshotDate.localeCompare(a.snapshotDate));
  },

  async reset(): Promise<void> {
    throw new Error('真实 API 模式不支持重置示例数据');
  },
};
