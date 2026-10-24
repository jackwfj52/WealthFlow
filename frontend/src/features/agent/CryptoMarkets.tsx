import { Card, Collapse, Space, Typography } from 'antd';
import type { CryptoMarketContext } from '../../services/apiCryptoMarket';

const { Text, Link } = Typography;
const time = (value: string) => value.replace('T', ' ').replace('Z', ' UTC');
export default function CryptoMarkets({ contexts }: { contexts: CryptoMarketContext[] }) {
  return <Collapse size="small" style={{ marginTop: 12, whiteSpace: 'normal' }} items={[{
    key: 'crypto', label: `币安行情依据 (${contexts.reduce((sum, context) => sum + context.markets.length, 0)})`,
    children: <Space orientation="vertical" size={8} style={{ width: '100%' }}>
      {contexts.map(context => <Card key={context.id} size="small" style={{ width: '100%' }}>
        <Space orientation="vertical" size={6}>
          <Text type="secondary">Binance Spot · [行情:{context.id}]</Text>
          <Text type="secondary">查询：{time(context.retrievedAt)}</Text>
          {context.markets.map(market => <Space key={market.symbol} orientation="vertical" size={2}>
            <Link href={`https://www.binance.com/en/trade/${market.symbol.replace(/USDT$/, '_USDT')}?type=spot`} target="_blank" rel="noopener noreferrer">{market.symbol}</Link>
            <Text>价格 {market.lastPrice} USDT · 滚动24小时 {market.changePercent24h}%</Text>
            <Text type="secondary">24小时最高 / 最低：{market.high24h} / {market.low24h} USDT</Text>
            <Text type="secondary">24小时成交额：{market.quoteVolume24h} USDT</Text>
            <Text>最近 {context.days} 个已完成 UTC 日：{market.periodChangePercent}%</Text>
            <Text type="secondary" style={{ fontSize: 12 }}>{time(market.periodStartTime)} 至 {time(market.periodEndTime)}</Text>
          </Space>)}
          <Text type="secondary" style={{ fontSize: 12 }}>{context.coverage}</Text>
        </Space>
      </Card>)}
    </Space>,
  }]} />;
}
