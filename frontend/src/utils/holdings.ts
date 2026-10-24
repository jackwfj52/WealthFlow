import type { HoldingInput } from '../types/domain';

export function validateHoldings(input: unknown): HoldingInput[] {
  if (!Array.isArray(input) || input.length > 200) throw new Error('每个类别最多登记200项持仓');
  const names = new Set<string>();
  let total = 0;
  const normalized = input.map((item): HoldingInput => {
    if (!item || typeof item.name !== 'string' || !item.name.trim() || item.name.trim().length > 80)
      throw new Error('标的名称不能为空且不超过80个字符');
    const name = item.name.trim();
    if (names.has(name.toLowerCase())) throw new Error(`同一类别的标的名称不能重复：${name}`);
    names.add(name.toLowerCase());
    const percentage = item.percentage;
    if (typeof percentage !== 'number' || !Number.isFinite(percentage) || percentage <= 0 || percentage > 100)
      throw new Error('持仓占比必须大于0且不超过100%');
    const bps = Math.round(percentage * 100);
    if (Math.abs(percentage * 100 - bps) > 1e-8) throw new Error('持仓占比最多保留两位小数');
    total += bps;
    return { name, percentage: bps / 100 };
  });
  if (total > 10000) throw new Error('同一资产类别的持仓占比合计不能超过100%');
  return normalized;
}

export const totalPercentage = (items: Partial<HoldingInput>[]) =>
  items.reduce((sum, item) => sum + Math.round((Number(item?.percentage) || 0) * 100), 0) / 100;

export const formatPercentage = (value: number) => `${Number(value.toFixed(2))}%`;
