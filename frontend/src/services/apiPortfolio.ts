export interface PortfolioContext {
  id: string;
  retrievedAt: string;
  snapshotDate: string | null;
  snapshotAgeDays: number | null;
  totalSnapshotAmount: number | string | null;
  registeredPortfolioPercent: number | null;
  sectorTaggedPortfolioPercent: number | null;
  categories: {
    categoryId: string;
    categoryName: string;
    portfolioPercent: number | null;
    registeredPercent: number;
    unclassifiedPercent: number;
    sectorExposure: { sector: string; label: string; categoryPercent: number; estimatedPortfolioPercent: number | null }[];
    holdings: { name: string; categoryPercent: number; sectorLabel: string; updatedAt: string | null }[];
  }[];
  limitations: string[];
}
