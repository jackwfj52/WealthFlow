import { Table, Typography } from 'antd';
import type { BatchSnapshotDraftResult, DraftSnapshotItem } from '../../services/apiAgentActions';

function details(items: DraftSnapshotItem[]) {
  if (!items.length) return <Typography.Text type="secondary">无快照</Typography.Text>;
  return items.map(item => <div key={item.categoryId}>{item.categoryName}：¥{item.amount}</div>);
}
export default function BatchDraftDetails({ draft }: { draft: BatchSnapshotDraftResult }) {
  return <Table size="small" rowKey="snapshotDate" dataSource={draft.changes}
    pagination={{ pageSize: 7, showSizeChanger: false, showTotal: total => `共 ${total} 天` }}
    scroll={{ x: 400 }} columns={[
      { title: '日期', dataIndex: 'snapshotDate', width: 105 },
      { title: '修改前', dataIndex: 'before', render: details },
      { title: '修改后', dataIndex: 'after', render: details },
    ]} />;
}
