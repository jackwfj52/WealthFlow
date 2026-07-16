import React, { useCallback } from 'react';
import { Card, Button, Space, Popconfirm, message, Divider, Typography } from 'antd';
import {
  DeleteOutlined,
  UndoOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import PageHeader from '../../components/PageHeader';
import { useCategories, useSnapshots } from '../../app/storage';
import { SEED_CATEGORIES, SEED_SNAPSHOTS } from '../../services/mockData';
import { categoryService } from '../../services/mockCategories';
import { snapshotService } from '../../services/mockSnapshots';

const Settings: React.FC = () => {
  const { refresh: refreshCategories } = useCategories();
  const { refresh: refreshSnapshots } = useSnapshots();

  const handleClearAll = useCallback(() => {
    categoryService.reset([]);
    snapshotService.reset([]);
    refreshCategories();
    refreshSnapshots();
    message.success('所有数据已清空');
  }, [refreshCategories, refreshSnapshots]);

  const handleRestoreSeed = useCallback(() => {
    categoryService.reset(SEED_CATEGORIES);
    snapshotService.reset(SEED_SNAPSHOTS);
    refreshCategories();
    refreshSnapshots();
    message.success('示例数据已恢复');
  }, [refreshCategories, refreshSnapshots]);

  return (
    <>
      <PageHeader title="设置" subtitle="数据管理与其他设置" />

      <Card title="数据管理" style={{ maxWidth: 600 }}>
        <Typography.Paragraph type="secondary">
          当前数据存储在浏览器 localStorage 中。清空数据不可恢复，建议先导出备份。
        </Typography.Paragraph>

        <Space direction="vertical" style={{ width: '100%' }}>
          <Button
            type="primary"
            icon={<UndoOutlined />}
            onClick={handleRestoreSeed}
          >
            恢复示例数据
          </Button>

          <Popconfirm
            title="确认清空所有数据？"
            description="此操作将删除所有分类和快照数据，且不可恢复。"
            onConfirm={handleClearAll}
            okText="确认清空"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button danger icon={<DeleteOutlined />}>
              清空所有数据
            </Button>
          </Popconfirm>
        </Space>

        <Divider />

        <Typography.Text type="secondary">
          <SettingOutlined /> 更多设置功能开发中...
        </Typography.Text>
      </Card>
    </>
  );
};

export default Settings;
