/**
 * 快照 JSON 导入导出
 *
 * 标准格式（导出与导入一致，可直接往返）：
 * [
 *   {
 *     "snapshotDate": "2026-08-01",
 *     "items": [
 *       { "categoryName": "现金", "amount": "100" },
 *       { "categoryName": "股票", "amount": "5000.50" }
 *     ]
 *   }
 * ]
 *
 * 导入规则：
 * - snapshotDate 必须为真实日期 YYYY-MM-DD 且不晚于今天
 * - amount 为正数、最多两位小数（数字或字符串均可）
 * - 同一快照内同一分类只能一条明细
 * - 分类按名称匹配，不存在则自动创建
 * - 数据库中已存在的日期整条跳过（不覆盖历史快照）
 */
import type { AssetSnapshot, SnapshotItem } from '../../types/domain';
import { isValidDateOnly } from '../../utils/date';
import { isValidAmount } from '../../utils/amount';
import { isValidHexColor } from '../../utils/color';
import type { CategoryService, SnapshotService, SystemService } from '../../services/types';

export interface ImportedItem {
  categoryName: string;
  amount: string;
}

export interface ImportedSnapshot {
  snapshotDate: string;
  items: ImportedItem[];
}

export interface SkippedEntry {
  date: string;
  reason: string;
}

export interface ParseReport {
  valid: ImportedSnapshot[];
  skipped: SkippedEntry[];
}

export type ParseResult =
  | { ok: true; report: ParseReport }
  | { ok: false; error: string };

export interface ImportRunReport {
  created: number;
  createdCategories: number;
  skipped: SkippedEntry[];
}

/** 示例模板（下载后可作为 AI 转换 Excel 的格式参考） */
export const IMPORT_TEMPLATE: string = JSON.stringify(
  [
    {
      snapshotDate: '2026-08-01',
      items: [
        { categoryName: '现金', amount: '100' },
        { categoryName: '股票', amount: '5000.50' },
      ],
    },
  ],
  null,
  2
);

/**
 * 解析导入文件：结构问题直接报错，条目级问题跳过并记录原因。
 * 数据库层面的冲突（日期已存在）在 runImport 阶段检查。
 */
export function parseImportJson(text: string): ParseResult {
  let raw: unknown;
  try {
    raw = JSON.parse(text);
  } catch {
    return { ok: false, error: 'JSON 解析失败，请确认文件是合法的 JSON 格式' };
  }

  if (!Array.isArray(raw) || raw.length === 0) {
    return { ok: false, error: '文件内容应为非空数组，格式见下方说明，可下载模板参考' };
  }

  const valid: ImportedSnapshot[] = [];
  const skipped: SkippedEntry[] = [];
  const seenDates = new Set<string>();

  raw.forEach((entry, index) => {
    const line = index + 1;

    if (!entry || typeof entry !== 'object') {
      skipped.push({ date: '', reason: `第 ${line} 条：结构无效（应为对象）` });
      return;
    }
    const e = entry as Record<string, unknown>;

    const date = typeof e.snapshotDate === 'string' ? e.snapshotDate : '';
    if (!date) {
      skipped.push({ date: '', reason: `第 ${line} 条：缺少 snapshotDate 字段` });
      return;
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      skipped.push({ date, reason: '日期格式无效，需为 YYYY-MM-DD' });
      return;
    }
    if (!isValidDateOnly(date)) {
      skipped.push({ date, reason: '日期无效（不存在或晚于今天）' });
      return;
    }
    if (seenDates.has(date)) {
      skipped.push({ date, reason: '文件内日期重复' });
      return;
    }
    if (!Array.isArray(e.items) || e.items.length === 0) {
      skipped.push({ date, reason: 'items 不能为空数组' });
      return;
    }

    const items: ImportedItem[] = [];
    const seenNames = new Set<string>();
    let invalidReason = '';

    for (const rawItem of e.items) {
      if (!rawItem || typeof rawItem !== 'object') {
        invalidReason = '存在无效的明细项';
        break;
      }
      const it = rawItem as Record<string, unknown>;
      const name = typeof it.categoryName === 'string' ? it.categoryName.trim() : '';
      const rawAmount = it.amount;
      const amount =
        typeof rawAmount === 'number'
          ? String(rawAmount)
          : typeof rawAmount === 'string'
            ? rawAmount.trim()
            : '';

      if (!name) {
        invalidReason = '分类名称为空';
        break;
      }
      if (seenNames.has(name)) {
        invalidReason = `分类"${name}"重复`;
        break;
      }
      if (!isValidAmount(amount)) {
        invalidReason = `分类"${name}"金额无效（需为正数，最多两位小数）`;
        break;
      }

      seenNames.add(name);
      items.push({ categoryName: name, amount });
    }

    if (invalidReason) {
      skipped.push({ date, reason: invalidReason });
      return;
    }

    seenDates.add(date);
    valid.push({ snapshotDate: date, items });
  });

  return { ok: true, report: { valid, skipped } };
}

/** 生成导出 JSON（与导入格式一致），按日期升序 */
export function buildExportJson(snapshots: AssetSnapshot[]): string {
  const data: ImportedSnapshot[] = [...snapshots]
    .sort((a, b) => a.snapshotDate.localeCompare(b.snapshotDate))
    .map((s) => ({
      snapshotDate: s.snapshotDate,
      items: s.items.map((item) => ({
        categoryName: item.categoryName,
        amount: item.amount,
      })),
    }));
  return JSON.stringify(data, null, 2);
}

/** 触发浏览器下载文本文件 */
export function downloadTextFile(filename: string, text: string): void {
  const blob = new Blob([text], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

/**
 * 执行导入：逐条创建快照。
 * 日期已存在 → 跳过；分类名不存在 → 自动创建。
 * 中途出错会抛出异常，由调用方提示（已创建的保留）。
 */
export async function runImport(
  snapshots: ImportedSnapshot[],
  categoryService: CategoryService,
  snapshotService: SnapshotService
): Promise<ImportRunReport> {
  const report: ImportRunReport = { created: 0, createdCategories: 0, skipped: [] };

  const categories = await categoryService.getAll();
  const nameToId = new Map(categories.map((c) => [c.name, c.id]));

  for (const entry of snapshots) {
    if (await snapshotService.existsByDate(entry.snapshotDate)) {
      report.skipped.push({
        date: entry.snapshotDate,
        reason: '数据库中已存在该日期的快照（不覆盖历史数据）',
      });
      continue;
    }

    const items: SnapshotItem[] = [];
    for (const item of entry.items) {
      let categoryId = nameToId.get(item.categoryName);
      if (!categoryId) {
        const created = await categoryService.create(item.categoryName);
        categoryId = created.id;
        nameToId.set(item.categoryName, categoryId);
        report.createdCategories += 1;
      }
      items.push({
        categoryId,
        categoryName: item.categoryName,
        amount: item.amount,
      });
    }

    await snapshotService.create(entry.snapshotDate, items);
    report.created += 1;
  }

  return report;
}

/* ---------------------------------------------------------------------------
 * 全量备份（分类 + 快照）
 * ------------------------------------------------------------------------- */

export interface BackupCategory {
  name: string;
  color?: string;
}

export interface BackupFile {
  type: string;
  version: number;
  exportedAt: string;
  categories: BackupCategory[];
  snapshots: ImportedSnapshot[];
}

export type BackupParseResult =
  | { ok: true; backup: BackupFile; report: ParseReport }
  | { ok: false; error: string };

export type RestoreMode = 'merge' | 'overwrite';

export interface BackupRestoreReport {
  createdSnapshots: number;
  createdCategories: number;
  skipped: SkippedEntry[];
}

/** 生成全量备份 JSON（分类 + 快照），快照按日期升序 */
export function buildBackupJson(
  categories: { name: string; color?: string }[],
  snapshots: AssetSnapshot[]
): string {
  const backup: BackupFile = {
    type: 'wealthflow-backup',
    version: 1,
    exportedAt: new Date().toISOString(),
    categories: categories.map((c) =>
      c.color ? { name: c.name, color: c.color } : { name: c.name }
    ),
    snapshots: [...snapshots]
      .sort((a, b) => a.snapshotDate.localeCompare(b.snapshotDate))
      .map((s) => ({
        snapshotDate: s.snapshotDate,
        items: s.items.map((item) => ({
          categoryName: item.categoryName,
          amount: item.amount,
        })),
      })),
  };
  return JSON.stringify(backup, null, 2);
}

/** 解析备份文件：结构问题直接报错，快照条目级问题跳过并记录原因 */
export function parseBackupJson(text: string): BackupParseResult {
  let raw: unknown;
  try {
    raw = JSON.parse(text);
  } catch {
    return { ok: false, error: 'JSON 解析失败，请确认文件是合法的 JSON 格式' };
  }

  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return { ok: false, error: '备份文件结构无效：顶层应为对象' };
  }
  const e = raw as Record<string, unknown>;

  if (typeof e.type === 'string' && e.type !== 'wealthflow-backup') {
    return { ok: false, error: '不是有效的 WealthFlow 备份文件' };
  }
  if (e.categories !== undefined && !Array.isArray(e.categories)) {
    return { ok: false, error: '备份文件 categories 字段应为数组' };
  }
  if (!Array.isArray(e.snapshots)) {
    return { ok: false, error: '备份文件 snapshots 字段应为数组' };
  }

  const categories: BackupCategory[] = [];
  for (const entry of e.categories ?? []) {
    const obj = entry && typeof entry === 'object' ? (entry as Record<string, unknown>) : null;
    const name =
      typeof entry === 'string'
        ? entry.trim()
        : String(obj?.name ?? '').trim();
    const rawColor = obj?.color;
    const color =
      typeof rawColor === 'string' && isValidHexColor(rawColor.trim())
        ? rawColor.trim().toLowerCase()
        : undefined;
    if (name && !categories.some((c) => c.name === name)) {
      categories.push(color ? { name, color } : { name });
    }
  }

  let report: ParseReport;
  if (e.snapshots.length === 0) {
    report = { valid: [], skipped: [] };
  } else {
    const parsed = parseImportJson(JSON.stringify(e.snapshots));
    if (!parsed.ok) {
      return { ok: false, error: `快照数据无效：${parsed.error}` };
    }
    report = parsed.report;
  }

  const backup: BackupFile = {
    type: typeof e.type === 'string' ? e.type : 'wealthflow-backup',
    version: typeof e.version === 'number' ? e.version : 1,
    exportedAt: typeof e.exportedAt === 'string' ? e.exportedAt : '',
    categories,
    snapshots: report.valid,
  };

  return { ok: true, backup, report };
}

/**
 * 执行备份恢复。
 * - merge：保留现有数据，已存在的快照日期跳过
 * - overwrite：先清空全部数据，再导入备份
 * 分类按名称匹配，不存在则自动创建。
 */
export async function runBackupRestore(
  backup: BackupFile,
  mode: RestoreMode,
  categoryService: CategoryService,
  snapshotService: SnapshotService,
  systemService: SystemService
): Promise<BackupRestoreReport> {
  if (mode === 'overwrite') {
    await systemService.clearAll();
  }

  const report: BackupRestoreReport = {
    createdSnapshots: 0,
    createdCategories: 0,
    skipped: [],
  };

  const categories = await categoryService.getAll();
  const nameToId = new Map(categories.map((c) => [c.name, c.id]));

  for (const cat of backup.categories) {
    if (!nameToId.has(cat.name)) {
      const created = await categoryService.create(cat.name, cat.color);
      nameToId.set(cat.name, created.id);
      report.createdCategories += 1;
    }
  }

  for (const entry of backup.snapshots) {
    if (await snapshotService.existsByDate(entry.snapshotDate)) {
      report.skipped.push({
        date: entry.snapshotDate,
        reason:
          mode === 'merge'
            ? '数据库中已存在该日期的快照（合并模式不覆盖）'
            : '数据库中已存在该日期的快照',
      });
      continue;
    }

    const items: SnapshotItem[] = [];
    for (const item of entry.items) {
      let categoryId = nameToId.get(item.categoryName);
      if (!categoryId) {
        const created = await categoryService.create(item.categoryName);
        categoryId = created.id;
        nameToId.set(item.categoryName, categoryId);
        report.createdCategories += 1;
      }
      items.push({
        categoryId,
        categoryName: item.categoryName,
        amount: item.amount,
      });
    }

    await snapshotService.create(entry.snapshotDate, items);
    report.createdSnapshots += 1;
  }

  return report;
}
