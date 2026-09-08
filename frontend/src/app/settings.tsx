import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { isValidHexColor } from '../utils/color';

export type ThemeMode = 'light' | 'dark';
export type TrendAggregation = 'day' | 'week' | 'month';

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
};

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
