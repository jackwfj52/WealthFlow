import { Collapse, Space, Typography } from 'antd';
import type { PortfolioContext } from '../../services/apiPortfolio';
import { formatPercentage } from '../../utils/holdings';

const { Text } = Typography;
const percentage = (value: number | null) => value == null ? '未知' : formatPercentage(value);

export default function PortfolioBasis({ context }: { context: PortfolioContext }) {
  return <Collapse size="small" style={{ marginTop: 12, whiteSpace: 'normal' }} items={[{
    key: 'portfolio', label: '持仓分析依据', children: <Space orientation="vertical" size={10} style={{ width: '100%' }}>
      <Text type="secondary">快照日期：{context.snapshotDate ?? '暂无'} · 查询时间：{context.retrievedAt.replace('T', ' ')}</Text>
      {context.categories.map(category => <div key={category.categoryId}>
        <Text strong>{category.categoryName}</Text>
        <div><Text type="secondary">占快照总资产 {percentage(category.portfolioPercent)} · 类内已登记 {percentage(category.registeredPercent)}</Text></div>
        {category.sectorExposure.map(sector => <div key={sector.sector}>
          <Text>{sector.label}：占本类 {percentage(sector.categoryPercent)} · 估算占总资产 {percentage(sector.estimatedPortfolioPercent)}</Text>
        </div>)}
        <Text type="secondary">类内行业未标注 {percentage(category.unclassifiedPercent)}</Text>
      </div>)}
      {context.limitations.map(note => <Text type="secondary" key={note} style={{ fontSize: 12 }}>{note}</Text>)}
    </Space>,
  }]} />;
}
