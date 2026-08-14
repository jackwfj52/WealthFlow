import React, {
  createContext,
  useContext,
  useState,
  useCallback,
  useEffect,
} from 'react';
import type { ReactNode } from 'react';
import { message } from 'antd';
import type { AssetCategory, AssetSnapshot } from '../types/domain';
import { USE_MOCK, categoryService, snapshotService } from '../services';
import { SEED_CATEGORIES, SEED_SNAPSHOTS } from '../services/mockData';

const INIT_FLAG_KEY = 'wealthflow_initialized';

interface AppContextType {
  categories: AssetCategory[];
  snapshots: AssetSnapshot[];
  loading: boolean;
  /** 初始加载失败时的错误信息（加载成功后不再自动清除，由 reload 重置） */
  error: string | null;
  refreshCategories: () => void;
  refreshSnapshots: () => void;
  reload: () => void;
}

const AppContext = createContext<AppContextType | null>(null);

/**
 * Mock 模式下仅在首次访问时写入种子数据。
 * 用户主动清空后不会再自动恢复（由 settings 页面的"恢复示例数据"按钮手动触发）。
 */
async function loadInitial(): Promise<{
  categories: AssetCategory[];
  snapshots: AssetSnapshot[];
}> {
  if (USE_MOCK) {
    const initialized = localStorage.getItem(INIT_FLAG_KEY);
    if (!initialized) {
      await categoryService.reset(SEED_CATEGORIES);
      await snapshotService.reset(SEED_SNAPSHOTS);
      localStorage.setItem(INIT_FLAG_KEY, '1');
      return { categories: SEED_CATEGORIES, snapshots: SEED_SNAPSHOTS };
    }
  }

  const [categories, snapshots] = await Promise.all([
    categoryService.getAll(),
    snapshotService.getAll(),
  ]);
  return { categories, snapshots };
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [categories, setCategories] = useState<AssetCategory[]>([]);
  const [snapshots, setSnapshots] = useState<AssetSnapshot[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadAll = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await loadInitial();
      setCategories(data.categories);
      setSnapshots(data.snapshots);
    } catch (err) {
      setError(err instanceof Error ? err.message : '数据加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  const refreshCategories = useCallback(() => {
    categoryService
      .getAll()
      .then(setCategories)
      .catch((err: unknown) => {
        message.error(err instanceof Error ? err.message : '分类数据加载失败');
      });
  }, []);

  const refreshSnapshots = useCallback(() => {
    snapshotService
      .getAll()
      .then(setSnapshots)
      .catch((err: unknown) => {
        message.error(err instanceof Error ? err.message : '快照数据加载失败');
      });
  }, []);

  const value: AppContextType = {
    categories,
    snapshots,
    loading,
    error,
    refreshCategories,
    refreshSnapshots,
    reload: loadAll,
  };

  return React.createElement(AppContext.Provider, { value }, children);
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used within AppProvider');
  return ctx;
}

export function useCategories() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useCategories must be used within AppProvider');
  return { categories: ctx.categories, refresh: ctx.refreshCategories };
}

export function useSnapshots() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useSnapshots must be used within AppProvider');
  return { snapshots: ctx.snapshots, loading: ctx.loading, refresh: ctx.refreshSnapshots };
}
