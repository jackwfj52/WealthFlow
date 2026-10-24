import type { AssetHolding, HoldingInput } from '../types/domain';
import { categoryService } from './mockCategories';
import { generateId } from './mockData';
import { validateHoldings } from '../utils/holdings';

export const HOLDINGS_STORAGE_KEY = 'wealthflow_holdings';
export const readMockHoldings = (): AssetHolding[] => JSON.parse(localStorage.getItem(HOLDINGS_STORAGE_KEY) ?? '[]');

export const mockHoldingService = {
  async getAll(): Promise<AssetHolding[]> {
    const ids = new Set((await categoryService.getAll()).map(category => category.id));
    return readMockHoldings().filter(item => ids.has(item.categoryId));
  },
  async replace(categoryId: string, input: HoldingInput[]): Promise<AssetHolding[]> {
    const items = validateHoldings(input);
    if (!await categoryService.getById(categoryId)) throw new Error('该资产分类不存在');
    const saved = items.map(item => ({ ...item, id: generateId(), categoryId }));
    localStorage.setItem(HOLDINGS_STORAGE_KEY,
      JSON.stringify([...readMockHoldings().filter(item => item.categoryId !== categoryId), ...saved]));
    return saved;
  },
};
