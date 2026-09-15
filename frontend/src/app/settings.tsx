import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { isValidHexColor } from '../utils/color';

export type ThemeMode = 'light' | 'dark';
export type TrendAggregation = 'day' | 'week' | 'month';
export type SnapshotDateOrder = 'desc' | 'asc';

/** 增长率颜色区间：from（不含）~ to（含），百分比；null 表示无界限 */
export interface RateBand {
  from: number | null;
  to: number | null;
  color: string;
}

/** 增长率标签颜色配置：0% 固定色 + 按顺序匹配的区间规则 */
export interface RateColorConfig {
  zero: string;
  bands: RateBand[];
}

export interface AppSettings {
  /** 明暗主题 */
  theme: ThemeMode;
  /** 主题色（antd colorPrimary 主色） */
  themeColor: string;
  /** 趋势分析默认时间范围（天数） */
  defaultTrendDays: number;
  /** 趋势分析默认聚合粒度 */
  defaultAggregation: TrendAggregation;
  /** 金额货币符号 */
  currencySymbol: string;
  /** 千分位分隔符 */
  thousandsSeparator: boolean;
  /** 键盘左右方向键切换快照日期 */
  keyboardSwitch: boolean;
  /** 增长率标签颜色规则 */
  rateColors: RateColorConfig;
  /** 快照表日期排序：desc 倒序（新→旧），asc 正序（旧→新） */
  snapshotDateOrder: SnapshotDateOrder;
}

const SETTINGS_KEY = 'wealthflow_settings';

export const DEFAULT_SETTINGS: AppSettings = {
  theme: 'light',
  themeColor: '#1677ff',
  defaultTrendDays: 30,
  defaultAggregation: 'day',
  currencySymbol: '¥',
  thousandsSeparator: true,
  keyboardSwitch: true,
  rateColors: {
    zero: '#8c8c8c',
    bands: [
      { from: 5, to: null, color: '#237804' },
      { from: 0, to: 5, color: '#52c41a' },
      { from: -5, to: 0, color: '#ff7875' },
      { from: null, to: -5, color: '#a8071a' },
    ],
  },
  snapshotDateOrder: 'desc',
};

/** 清洗增长率颜色配置，防止异常值破坏渲染 */
function sanitizeRateColors(value: unknown): RateColorConfig {
  if (typeof value !== 'object' || value === null) return DEFAULT_SETTINGS.rateColors;
  const cfg = value as Partial<RateColorConfig>;
  const zero =
    typeof cfg.zero === 'string' && isValidHexColor(cfg.zero)
      ? cfg.zero
      : DEFAULT_SETTINGS.rateColors.zero;
  if (!Array.isArray(cfg.bands)) return DEFAULT_SETTINGS.rateColors;
  const bands: RateBand[] = [];
  for (const b of cfg.bands) {
    if (typeof b !== 'object' || b === null) continue;
    const raw = b as Partial<RateBand>;
    const color = typeof raw.color === 'string' ? raw.color : '';
    const from = typeof raw.from === 'number' && Number.isFinite(raw.from) ? raw.from : null;
    const to = typeof raw.to === 'number' && Number.isFinite(raw.to) ? raw.to : null;
    if (!isValidHexColor(color)) continue;
    if (from === null && to === null) continue;
    if (from !== null && to !== null && from >= to) continue;
    bands.push({ from, to, color });
  }
  return { zero, bands };
}

function loadSettings(): AppSettings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (!raw) return DEFAULT_SETTINGS;
    const parsed = JSON.parse(raw) as Partial<AppSettings>;
    const merged = { ...DEFAULT_SETTINGS, ...parsed };
    // 主题色必须为合法 hex，防止异常值破坏 antd 主题
    if (typeof merged.themeColor !== 'string' || !isValidHexColor(merged.themeColor)) {
      merged.themeColor = DEFAULT_SETTINGS.themeColor;
    }
    merged.rateColors = sanitizeRateColors(merged.rateColors);
    if (merged.snapshotDateOrder !== 'asc' && merged.snapshotDateOrder !== 'desc') {
      merged.snapshotDateOrder = DEFAULT_SETTINGS.snapshotDateOrder;
    }
    return merged;
  } catch {
    return DEFAULT_SETTINGS;
  }
}

interface SettingsContextType {
  settings: AppSettings;
  updateSettings: (patch: Partial<AppSettings>) => void;
  resetSettings: () => void;
}

const SettingsContext = createContext<SettingsContextType | null>(null);

export function SettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<AppSettings>(loadSettings);

  useEffect(() => {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
  }, [settings]);

  const updateSettings = (patch: Partial<AppSettings>) => {
    setSettings((prev) => ({ ...prev, ...patch }));
  };

  const resetSettings = () => setSettings(DEFAULT_SETTINGS);

  const value: SettingsContextType = { settings, updateSettings, resetSettings };

  return (
    <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>
  );
}

export function useSettings() {
  const ctx = useContext(SettingsContext);
  if (!ctx) throw new Error('useSettings must be used within SettingsProvider');
  return ctx;
}
