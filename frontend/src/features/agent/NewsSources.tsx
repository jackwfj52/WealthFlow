import { Card, Collapse, Space, Typography } from 'antd';
import type { FinancialNewsArticle } from '../../services/apiFinancialNews';

const { Text, Paragraph, Link } = Typography;
const sourceUrl = (value: string) => {
  try {
    const url = new URL(value);
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : undefined;
  } catch { return undefined; }
};

export default function NewsSources({ sources }: { sources: FinancialNewsArticle[] }) {
  return <Collapse size="small" style={{ marginTop: 12, whiteSpace: 'normal' }} items={[{
    key: 'news', label: `财经讯息来源 (${sources.length})`,
    children: <Space orientation="vertical" size={8} style={{ width: '100%' }}>
      {sources.map(source => <Card key={source.id} size="small" style={{ width: '100%' }}>
        <Space orientation="vertical" size={4} style={{ width: '100%' }}>
          {sourceUrl(source.url)
            ? <Link href={sourceUrl(source.url)} target="_blank" rel="noopener noreferrer">{source.title}</Link>
            : <Text strong>{source.title}</Text>}
          <Text type="secondary" style={{ fontSize: 12 }}>{source.publisher} · [资讯:{source.id}]</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>发布时间：{source.publishedAt.replace('T', ' ')}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>查询时间：{source.retrievedAt.replace('T', ' ')}</Text>
          {source.summary && <Paragraph ellipsis={{ rows: 3, expandable: true, symbol: '展开' }} style={{ margin: 0, fontSize: 12 }}>{source.summary}</Paragraph>}
        </Space>
      </Card>)}
      <Text type="secondary" style={{ fontSize: 12 }}>来源：AKShare / 东方财富。仅覆盖本次接口返回的近期结果。</Text>
    </Space>,
  }]} />;
}
