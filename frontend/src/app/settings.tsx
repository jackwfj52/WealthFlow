import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { DEFAULT_SETTINGS, normalizeSettings, type AppSettings } from './settingsValues';
export { DEFAULT_SETTINGS } from './settingsValues';
export type { AppSettings, ThemeMode, TrendAggregation, SnapshotDateOrder, RateBand, RateColorConfig } from './settingsValues';

const SETTINGS_KEY = 'wealthflow_settings';

function loadSettings(): AppSettings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (!raw) return DEFAULT_SETTINGS;
    return normalizeSettings(JSON.parse(raw));
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
