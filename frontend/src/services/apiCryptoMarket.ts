export interface CryptoMarketContext {
  id: string;
  provider: string;
  retrievedAt: string;
  days: number;
  coverage: string;
  markets: {
    symbol: string; quoteAsset: string; lastPrice: number | string; changePercent24h: number | string;
    high24h: number | string; low24h: number | string; volume24h: number | string; quoteVolume24h: number | string;
    windowOpenTime: string; windowCloseTime: string; periodStartTime: string; periodEndTime: string;
    periodChangePercent: number | string; url: string;
  }[];
}
