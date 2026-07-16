/** 资产分类 */
export interface AssetCategory {
  id: string;
  name: string;
  createdAt: string; // YYYY-MM-DD
}

/** 快照明细项 —— 某一天某个分类的金额 */
export interface SnapshotItem {
  categoryId: string;
  categoryName: string;
  amount: string; // 字符串存储，避免浮点精度问题
}

/**
 * 资产快照
 *
 * 核心规则：
 * - 一个快照对应一个 snapshotDate（YYYY-MM-DD）
 * - 每日资产更新 = 新增快照，不覆盖历史数据
 * - 同一日期同一分类最多一条明细
 * - 编辑快照只影响指定日期，不波及其他日期
 */
export interface AssetSnapshot {
  id: string;
  snapshotDate: string; // YYYY-MM-DD
  items: SnapshotItem[];
  totalAmount: string;
}

/** localStorage 存储结构 */
export interface AppStorage {
  categories: AssetCategory[];
  snapshots: AssetSnapshot[];
}
