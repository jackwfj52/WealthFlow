/** 将 #RRGGBB 十六进制颜色转为 rgba() 字符串 */
export function hexToRgba(hex: string, alpha: number): string {
  const h = hex.replace('#', '');
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/** 默认分类调色板（antd 经典色板） */
export const DEFAULT_CATEGORY_COLORS = [
  '#1677ff', '#52c41a', '#faad14', '#f5222d', '#722ed1',
  '#13c2c2', '#eb2f96', '#fa8c16', '#2f54eb', '#a0d911',
];

/** 校验是否为 #RRGGBB 十六进制颜色 */
export function isValidHexColor(value: string): boolean {
  return /^#[0-9a-fA-F]{6}$/.test(value);
}

/** 取分类实际颜色：未设置时按位置回退调色板 */
export function getCategoryColor(
  category: { color?: string } | undefined,
  index: number
): string {
  if (category?.color && isValidHexColor(category.color)) {
    return category.color.toLowerCase();
  }
  return DEFAULT_CATEGORY_COLORS[index % DEFAULT_CATEGORY_COLORS.length];
}

/** 为新建分类挑选一个未被使用的调色板颜色 */
export function pickUnusedCategoryColor(categories: { color?: string }[]): string {
  const used = new Set(
    categories.map((c) => c.color?.toLowerCase()).filter((c): c is string => !!c)
  );
  const unused = DEFAULT_CATEGORY_COLORS.find((c) => !used.has(c));
  return unused ?? DEFAULT_CATEGORY_COLORS[categories.length % DEFAULT_CATEGORY_COLORS.length];
}
