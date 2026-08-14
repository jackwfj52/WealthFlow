import React, { useCallback } from 'react';
import {
  Card,
  Button,
  Space,
  Popconfirm,
  message,
  Divider,
  Typography,
  Segmented,
  Select,
  Switch,
} from 'antd';
import {
  DeleteOutlined,
  UndoOutlined,
} from '@ant-design/icons';
import PageHeader from '../../components/PageHeader';
import { useCategories, useSnapshots } from '../../app/storage';
import { useSettings, type ThemeMode } from '../../app/settings';
import { SEED_CATEGORIES, SEED_SNAPSHOTS } from '../../services/mockData';
import { USE_MOCK, categoryService, snapshotService } from '../../services';

const SettingRow: React.FC<{ label: string; children: React.ReactNode }> = ({
  label,
  children,
}) => (
  <div
    style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      gap: 16,
    }}
  >
    <Typography.Text>{label}</Typography.Text>
    {children}
  </div>
);

const Settings: React.FC = () => {
  const { refresh: refreshCategories } = useCategories();
  const { refresh: refreshSnapshots } = useSnapshots();
  const { settings, updateSettings, resetSettings } = useSettings();

  const handleClearAll = useCallback(async () => {
    await categoryService.reset([]);
    await snapshotService.reset([]);
    refreshCategories();
    refreshSnapshots();
    message.success('所有数据已清空');
  }, [refreshCategories, refreshSnapshots]);

  const handleRestoreSeed = useCallback(async () => {
    await categoryService.reset(SEED_CATEGORIES);
    await snapshotService.reset(SEED_SNAPSHOTS);
    refreshCategories();
    refreshSnapshots();
    message.success('示例数据已恢复');
  }, [refreshCategories, refreshSnapshots]);

  return (
    <>
      <PageHeader title="设置" subtitle="数据管理、显示偏好与其他设置" />

      <Card title="显示偏好" style={{ maxWidth: 600, marginBottom: 16 }}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <SettingRow label="主题">
            <Segmented
              options={[
                { label: '浅色', value: 'light' },
                { label: '深色', value: 'dark' },
              ]}
              value={settings.theme}
              onChange={(v) => updateSettings({ theme: v as ThemeMode })}
            />
          </SettingRow>
          <SettingRow label="货币符号">
            <Select
              style={{ width: 140 }}
              value={settings.currencySymbol}
              onChange={(v) => updateSettings({ currencySymbol: v })}
              options={[
                { label: '¥ 人民币', value: '¥' },
                { label: '$ 美元', value: '$' },
                { label: '€ 欧元', value: '€' },
                { label: '£ 英镑', value: '£' },
              ]}
            />
          </SettingRow>
          <SettingRow label="千分位分隔符">
            <Switch
              checked={settings.thousandsSeparator}
              onChange={(v) => updateSettings({ thousandsSeparator: v })}
            />
          </SettingRow>
        </Space>
      </Card>

      <Card title="键盘快捷键" style={{ maxWidth: 600, marginBottom: 16 }}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <SettingRow label="启用键盘切换日期">
            <Switch
              checked={settings.keyboardSwitch}
              onChange={(v) => updateSettings({ keyboardSwitch: v })}
            />
          </SettingRow>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            资产总览页：← → 切换快照日期（点按单次切换，长按连续切换）。
            <br />
            仅在未聚焦输入框、按钮等控件时生效，避免干扰其他操作。
          </Typography.Paragraph>
        </Space>
      </Card>

      <Card title="数据管理" style={{ maxWidth: 600 }}>
        {USE_MOCK ? (
          <>
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
          </>
        ) : (
          <Typography.Paragraph type="secondary">
            当前为真实 API 模式，数据存储在服务端数据库中，本页面不提供数据重置功能。
          </Typography.Paragraph>
        )}

        <Divider />

        <Button type="link" onClick={resetSettings} style={{ padding: 0 }}>
          恢复默认设置
        </Button>
      </Card>
    </>
  );
};

export default Settings;
