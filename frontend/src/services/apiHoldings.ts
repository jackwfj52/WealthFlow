import { apiClient } from './apiClient';
import type { AssetHolding, HoldingInput } from '../types/domain';
import { validateHoldings } from '../utils/holdings';

export const apiHoldingService = {
  getAll: () => apiClient.get<AssetHolding[]>('/holdings'),
  replace: (categoryId: string, items: HoldingInput[]) =>
    apiClient.put<AssetHolding[]>(`/holdings/${encodeURIComponent(categoryId)}`, { items: validateHoldings(items) }),
};
