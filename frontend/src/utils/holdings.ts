import type { HoldingInput } from '../types/domain';

export const SECTOR_OPTIONS = [
  { value: 'TECH', label: '科技' }, { value: 'FINANCE', label: '金融' },
  { value: 'CONSUMER', label: '消费' }, { value: 'HEALTHCARE', label: '医疗' },
  { value: 'INDUSTRIAL', label: '工业' }, { value: 'ENERGY', label: '能源' },
  { value: 'MATERIALS', label: '材料' }, { value: 'UTILITIES', label: '公用事业' },
  { value: 'REAL_ESTATE', label: '房地产' }, { value: 'COMMUNICATION', label: '通信服务' },
  { value: 'PRECIOUS_METALS', label: '贵金属' }, { value: 'CRYPTO', label: '加密货币' },
  { value: 'MIXED', label: '混合/宽基' }, { value: 'OTHER', label: '其他' },
];
export const sectorLabel = (sector?: string) => SECTOR_OPTIONS.find(item => item.value === sector)?.label ?? '未标注';

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
    const sector = typeof item.sector === 'string' ? item.sector.trim() : item.sector;
    if (sector != null && sector !== '' && !SECTOR_OPTIONS.some(option => option.value === sector))
      throw new Error('行业标签无效，请选择支持的行业或留空');
    return { name, percentage: bps / 100, ...(sector ? { sector } : {}) };
  });
  if (total > 10000) throw new Error('同一资产类别的持仓占比合计不能超过100%');
  return normalized;
}

export const totalPercentage = (items: Partial<HoldingInput>[]) =>
  items.reduce((sum, item) => sum + Math.round((Number(item?.percentage) || 0) * 100), 0) / 100;

export const formatPercentage = (value: number) => `${Number(value.toFixed(2))}%`;
