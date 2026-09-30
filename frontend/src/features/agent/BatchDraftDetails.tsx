import { Alert, Table, Typography } from 'antd';
import dayjs from 'dayjs';
import type { BatchSnapshotDraftResult, DraftSnapshotItem } from '../../services/apiAgentActions';

function details(items: DraftSnapshotItem[]) {
  if (!items.length) return <Typography.Text type="secondary">无快照</Typography.Text>;
  return items.map(item => <div key={item.categoryId}>{item.categoryName}：¥{item.amount}</div>);
}
function amountTotal(items: DraftSnapshotItem[]) {
  return items.reduce((sum, item) => sum + Number(item.amount), 0);
}
function dailyRate(amount: number, previous?: number) {
  if (previous === undefined || previous <= 0) return null;
  const percent = ((amount - previous) / previous) * 100;
  return `${percent >= 0 ? '+' : ''}${percent.toFixed(2)}%`;
}
export default function BatchDraftDetails({ draft }: { draft: BatchSnapshotDraftResult }) {
  const simulated = draft.simulated;
  const rows = draft.changes.map((change, index) => {
    const previous = draft.changes[index - 1];
    return {
      ...change,
      previousDay: simulated && previous && dayjs(change.snapshotDate).diff(dayjs(previous.snapshotDate), 'day') === 1
        ? previous.after : undefined,
    };
  });
  return <>
    {simulated && <Alert type="warning" showIcon style={{ marginBottom: 12 }}
      message="这些金额是生成的模拟数据"
      description="确认后会作为普通快照保存；当前系统不会为已保存快照记录模拟来源。请逐日核对，勿将其误认为真实资产。" />}
    <Table size="small" rowKey="snapshotDate" dataSource={rows}
    pagination={{ pageSize: 7, showSizeChanger: false, showTotal: total => `共 ${total} 天` }}
    scroll={{ x: 400 }} columns={[
      { title: '日期', dataIndex: 'snapshotDate', width: 105 },
      { title: '修改前', dataIndex: 'before', render: details },
      { title: '修改后', render: (_, row) => <>
        {row.after.map(item => {
          const prior = row.previousDay?.find(before => before.categoryId === item.categoryId);
          const rate = dailyRate(Number(item.amount), prior ? Number(prior.amount) : undefined);
          return <div key={item.categoryId}>{item.categoryName}：¥{item.amount}{rate ? `（${rate}）` : ''}</div>;
        })}
        {simulated && row.after.length > 0 && <Typography.Text strong>
          总额：¥{amountTotal(row.after).toFixed(2)}
          {(() => {
            const rate = dailyRate(amountTotal(row.after), row.previousDay ? amountTotal(row.previousDay) : undefined);
            return rate ? `（${rate}）` : '';
          })()}
        </Typography.Text>}
      </> },
    ]} />
  </>;
}
