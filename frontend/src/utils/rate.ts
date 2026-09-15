import type { RateColorConfig } from '../app/settings';

/**
 * 计算增长率（百分比）。
 * 前值缺失或为 0 时无法计算，返回 null。
 */
export function calcGrowthRate(current: string, previous: string): number | null {
  const c = Number(current);
  const p = Number(previous);
  if (!Number.isFinite(c) || !Number.isFinite(p) || p === 0) return null;
  return ((c - p) / p) * 100;
}

/** 按档位规则取增长率颜色：0% 用固定色，其余按区间规则顺序匹配，无匹配回退 0% 色 */
export function bandColorOf(rate: number, cfg: RateColorConfig): string {
  if (rate === 0) return cfg.zero;
  for (const band of cfg.bands) {
    const fromOk = band.from == null || rate > band.from;
    const toOk = band.to == null || rate <= band.to;
    if (fromOk && toOk) return band.color;
  }
  return cfg.zero;
}

/** 增长率展示文案：带正负号，|r|≥10 保留 1 位小数，否则 2 位 */
export function formatRate(rate: number): string {
  const digits = Math.abs(rate) >= 10 ? 1 : 2;
  return `${rate > 0 ? '+' : ''}${rate.toFixed(digits)}%`;
}
