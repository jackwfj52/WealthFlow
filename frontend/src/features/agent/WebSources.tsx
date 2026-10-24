import { Card, Collapse, Space, Typography } from 'antd';
import type { WebSearchSource } from '../../services/apiWebSearch';

const { Text, Paragraph, Link } = Typography;
const sourceUrl = (value: string) => {
  try {
    const url = new URL(value);
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : undefined;
  } catch { return undefined; }
};

export default function WebSources({ sources }: { sources: WebSearchSource[] }) {
  return <Collapse size="small" style={{ marginTop: 12, whiteSpace: 'normal' }} items={[{
    key: 'web', label: `联网搜索来源 (${sources.length})`,
    children: <Space orientation="vertical" size={8} style={{ width: '100%' }}>
      {sources.map(source => <Card key={source.id} size="small" style={{ width: '100%' }}>
        <Space orientation="vertical" size={4} style={{ width: '100%' }}>
          {sourceUrl(source.url)
            ? <Link href={sourceUrl(source.url)} target="_blank" rel="noopener noreferrer">{source.title}</Link>
            : <Text strong>{source.title}</Text>}
          <Text type="secondary" style={{ fontSize: 12 }}>{source.siteName} · [网页:{source.id}]</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>发布时间：{source.publishedAt?.replace('T', ' ') ?? '搜索结果未提供'}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>查询时间：{source.retrievedAt.replace('T', ' ')}</Text>
          {source.snippet && <Paragraph ellipsis={{ rows: 3, expandable: true, symbol: '展开' }} style={{ margin: 0, fontSize: 12 }}>{source.snippet}</Paragraph>}
        </Space>
      </Card>)}
      <Text type="secondary" style={{ fontSize: 12 }}>仅展示搜索引擎返回的标题、链接和摘要，未读取网页全文。时间筛选不保证页面发布日期。</Text>
    </Space>,
  }]} />;
}
