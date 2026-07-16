/**
 * 金额工具函数
 *
 * 约定：金额统一用字符串存储，内部使用"分"（整数）计算避免浮点精度问题。
 * 显示时保留两位小数 + 千分位。
 */

/** 元字符串 → 分（整数） */
function toCents(yuan: string): number {
  const cleaned = (yuan || '0').replace(/[^\d.-]/g, '');
  const num = parseFloat(cleaned);
  if (isNaN(num)) return 0;
  return Math.round(num * 100);
}

/** 分（整数）→ 元字符串 */
function fromCents(cents: number): string {
  return (cents / 100).toFixed(2);
}

/** 格式化金额显示：保留两位小数，千分位逗号分隔 */
export function formatAmount(amount: string | number): string {
  // 接受字符串或数字，统一转为元字符串再做千分位格式化
  let yuan: string;
  if (typeof amount === 'number') {
    yuan = amount.toFixed(2);
  } else {
    const val = parseFloat(amount);
    if (isNaN(val)) return '0.00';
    yuan = val.toFixed(2);
  }
  const parts = yuan.split('.');
  parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return parts.join('.');
}

/** 多数累加（高精度：基于分计算），返回元字符串 */
export function sumAmounts(amounts: string[]): string {
  const totalCents = amounts.reduce((acc, a) => acc + toCents(a), 0);
  return fromCents(totalCents);
}

/** 两数相减 a - b，返回元字符串 */
export function subtractAmounts(a: string, b: string): string {
  return fromCents(toCents(a) - toCents(b));
}

/** 两个金额字符串相加 */
export function addAmounts(a: string, b: string): string {
  return fromCents(toCents(a) + toCents(b));
}

/** 验证金额格式：正整数或最多两位小数，且 > 0 */
export function isValidAmount(value: string): boolean {
  if (!value || !value.trim()) return false;
  return /^\d+(\.\d{1,2})?$/.test(value.trim()) && parseFloat(value) > 0;
}
