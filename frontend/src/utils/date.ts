/**
 * 日期工具函数
 *
 * 所有日期统一使用 YYYY-MM-DD，底层基于 dayjs + customParseFormat 严格解析。
 * 禁止使用 new Date('YYYY-MM-DD')，避免时区问题。
 */
import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';

dayjs.extend(customParseFormat);

// ---------- 基础格式化 ----------

/** 格式化 Date 为 YYYY-MM-DD */
export function formatDate(d: Date): string {
  return dayjs(d).format('YYYY-MM-DD');
}

/** 今天的 YYYY-MM-DD */
export function today(): string {
  return dayjs().format('YYYY-MM-DD');
}

/** N 天前的日期 */
export function daysAgo(n: number): string {
  return dayjs().subtract(n, 'day').format('YYYY-MM-DD');
}

// ---------- 日期合法性校验 ----------

/**
 * 校验是否为合法的 YYYY-MM-DD 真实日期（不含未来日期限制）。
 * 使用 dayjs 严格解析，只接受 YYYY-MM-DD 格式。
 *
 * 合法示例：2026-02-28, 2024-02-29
 * 非法示例：2026-02-30, 2026-04-31, 2026-13-01, 2026-1-1, 2026-99-99
 */
export function isValidDateFormat(value: unknown): value is string {
  if (typeof value !== 'string') return false;

  // 先用正则快速过滤明显不符合格式的输入
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;

  // dayjs 严格模式解析：第三个参数 true = strict parsing
  const parsed = dayjs(value, 'YYYY-MM-DD', true);

  // dayjs 宽松解析可能修正非法日期（如 2026-02-30 → 2026-03-02），
  // 需要回写比对确保输入日期未被自动纠正
  return parsed.isValid() && parsed.format('YYYY-MM-DD') === value;
}

/**
 * 校验日期格式合法且不能晚于今天（业务日期校验）。
 * 用于快照日期等不允许未来日期的场景。
 */
export function isValidDateOnly(value: unknown): value is string {
  if (!isValidDateFormat(value)) return false;
  return !dayjs(value, 'YYYY-MM-DD', true).isAfter(dayjs(), 'day');
}

/**
 * 校验日期范围：两个日期都合法且开始 ≤ 结束
 */
export function isValidDateRange(start: unknown, end: unknown): { valid: boolean; error?: string } {
  if (!isValidDateFormat(start)) {
    return { valid: false, error: `开始日期"${String(start)}"无效，请输入真实日期（YYYY-MM-DD）` };
  }
  if (!isValidDateFormat(end)) {
    return { valid: false, error: `结束日期"${String(end)}"无效，请输入真实日期（YYYY-MM-DD）` };
  }
  if (start > end) {
    return { valid: false, error: `开始日期（${start}）不能晚于结束日期（${end}）` };
  }
  return { valid: true };
}

// ---------- 日期范围与聚合 ----------

/** 获取日期范围内的所有日期 */
export function dateRange(start: string, end: string): string[] {
  const dates: string[] = [];
  let cursor = dayjs(start, 'YYYY-MM-DD', true);
  const endDay = dayjs(end, 'YYYY-MM-DD', true);
  while (cursor.isBefore(endDay) || cursor.isSame(endDay, 'day')) {
    dates.push(cursor.format('YYYY-MM-DD'));
    cursor = cursor.add(1, 'day');
  }
  return dates;
}

/** 日期字符串比较 */
export function compareDate(a: string, b: string): number {
  return a.localeCompare(b);
}

/** 判断是否同一天 */
export function isSameDate(a: string, b: string): boolean {
  return a === b;
}

/** 周日（一周从周日开始） */
export function getWeekStart(dateStr: string): string {
  return dayjs(dateStr, 'YYYY-MM-DD', true).day(0).format('YYYY-MM-DD');
}

/** 月初 */
export function getMonthStart(dateStr: string): string {
  return dayjs(dateStr, 'YYYY-MM-DD', true).date(1).format('YYYY-MM-DD');
}
