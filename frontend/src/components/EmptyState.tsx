import React from 'react';
import { Empty, Button } from 'antd';
import { PlusOutlined } from '@ant-design/icons';

interface Props {
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
}

const EmptyState: React.FC<Props> = ({
  description = '暂无数据',
  actionLabel,
  onAction,
}) => (
  <Empty
    description={description}
    style={{ padding: '60px 0' }}
  >
    {actionLabel && onAction && (
      <Button type="primary" icon={<PlusOutlined />} onClick={onAction}>
        {actionLabel}
      </Button>
    )}
  </Empty>
);

export default EmptyState;
