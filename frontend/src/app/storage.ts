import React, {
  createContext,
  useContext,
  useState,
  useCallback,
} from 'react';
import type { ReactNode } from 'react';
import type { AssetCategory, AssetSnapshot } from '../types/domain';
import { categoryService } from '../services/mockCategories';
import { snapshotService } from '../services/mockSnapshots';
import { SEED_CATEGORIES, SEED_SNAPSHOTS } from '../services/mockData';

const INIT_FLAG_KEY = 'wealthflow_initialized';

interface AppContextType {
  categories: AssetCategory[];
  snapshots: AssetSnapshot[];
  loading: boolean;
  refreshCategories: () => void;
  refreshSnapshots: () => void;
}

const AppContext = createContext<AppContextType | null>(null);

/**
 * 仅在首次访问时写入种子数据。
 * 用户主动清空后不会再自动恢复（由 settings 页面的"恢复示例数据"按钮手动触发）。
 */
function loadInitial(): { categories: AssetCategory[]; snapshots: AssetSnapshot[] } {
  const initialized = localStorage.getItem(INIT_FLAG_KEY);

  if (!initialized) {
    categoryService.reset(SEED_CATEGORIES);
    snapshotService.reset(SEED_SNAPSHOTS);
    localStorage.setItem(INIT_FLAG_KEY, '1');
    return { categories: SEED_CATEGORIES, snapshots: SEED_SNAPSHOTS };
  }

  return {
    categories: categoryService.getAll(),
    snapshots: snapshotService.getAll(),
  };
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState(loadInitial);

  const refreshCategories = useCallback(() => {
    setData((prev) => ({ ...prev, categories: categoryService.getAll() }));
  }, []);

  const refreshSnapshots = useCallback(() => {
    setData((prev) => ({ ...prev, snapshots: snapshotService.getAll() }));
  }, []);

  const value: AppContextType = {
    ...data,
    loading: false,
    refreshCategories,
    refreshSnapshots,
  };

  return React.createElement(AppContext.Provider, { value }, children);
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
