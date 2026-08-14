import React, { useCallback, useEffect, useRef, useState } from 'react';
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
  Descriptions,
  Spin,
  Modal,
  Radio,
  Alert,
} from 'antd';
import {
  DeleteOutlined,
  UndoOutlined,
  ReloadOutlined,
  DownloadOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import PageHeader from '../../components/PageHeader';
import { useCategories, useSnapshots } from '../../app/storage';
import { useSettings, type ThemeMode } from '../../app/settings';
import { SEED_CATEGORIES, SEED_SNAPSHOTS } from '../../services/mockData';
import { USE_MOCK, categoryService, snapshotService, systemService } from '../../services';
import type { SystemInfo } from '../../services/types';
import {
  buildBackupJson,
  parseBackupJson,
  runBackupRestore,
  downloadTextFile,
  type BackupFile,
  type ImportedSnapshot,
  type SkippedEntry,
  type RestoreMode,
} from '../snapshots/importExport';

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
  const { snapshots, refresh: refreshSnapshots } = useSnapshots();
  const { settings, updateSettings, resetSettings } = useSettings();
  const [sysInfo, setSysInfo] = useState<SystemInfo | null>(null);
  const [sysInfoLoading, setSysInfoLoading] = useState(false);

  const refreshSysInfo = useCallback(async () => {
    setSysInfoLoading(true);
    try {
      setSysInfo(await systemService.getInfo());
    } catch {
      message.error('数据库信息获取失败');
    } finally {
      setSysInfoLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshSysInfo();
  }, [refreshSysInfo]);

  const latestDate = snapshots.length
    ? snapshots.map((s) => s.snapshotDate).sort().at(-1)
    : null;

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [restorePending, setRestorePending] = useState<{
    backup: BackupFile;
    valid: ImportedSnapshot[];
    skipped: SkippedEntry[];
  } | null>(null);
  const [restoreMode, setRestoreMode] = useState<RestoreMode>('merge');
  const [restoring, setRestoring] = useState(false);

  const handleExportAll = useCallback(async () => {
    const [categories, snapshots] = await Promise.all([
      categoryService.getAll(),
      snapshotService.getAll(),
    ]);
    const json = buildBackupJson(categories, snapshots);
    downloadTextFile(
      `wealthflow-backup-${dayjs().format('YYYY-MM-DD')}.json`,
      json
    );
    message.success(
      `已导出 ${categories.length} 个分类、${snapshots.length} 条快照`
    );
  }, []);

  const handleBackupFileChange = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      e.target.value = '';
      if (!file) return;
      const text = await file.text();
      const result = parseBackupJson(text);
      if (!result.ok) {
        message.error(result.error);
        return;
      }
      setRestoreMode('merge');
      setRestorePending({
        backup: result.backup,
        valid: result.report.valid,
        skipped: result.report.skipped,
      });
    },
    []
  );

  const handleConfirmRestore = useCallback(async () => {
    if (!restorePending) return;
    setRestoring(true);
    try {
      const report = await runBackupRestore(
        restorePending.backup,
        restoreMode,
        categoryService,
        snapshotService,
        systemService
      );
      refreshCategories();
      refreshSnapshots();
      void refreshSysInfo();
      setRestorePending(null);
      message.success(
        `恢复完成：新增 ${report.createdSnapshots} 条快照、${report.createdCategories} 个分类` +
          (report.skipped.length
            ? `，跳过 ${report.skipped.length} 条已存在日期的快照`
            : '')
      );
    } catch (err) {
      message.error(err instanceof Error ? err.message : '恢复失败');
    } finally {
      setRestoring(false);
    }
  }, [
    restorePending,
    restoreMode,
    refreshCategories,
    refreshSnapshots,
    refreshSysInfo,
  ]);

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

  const handleClearAllApi = useCallback(async () => {
    await systemService.clearAll();
    refreshCategories();
    refreshSnapshots();
    void refreshSysInfo();
    message.success('所有数据已清空');
  }, [refreshCategories, refreshSnapshots, refreshSysInfo]);

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
          <>
            <Typography.Paragraph type="secondary">
              当前为真实 API 模式，数据存储在服务端数据库中。清空将删除所有分类与快照数据，不可恢复，建议先导出备份。
            </Typography.Paragraph>

            <Popconfirm
              title="确认清空所有数据？"
              description="此操作将删除服务端数据库中的所有分类和快照数据。"
              onConfirm={() => {
                Modal.confirm({
                  title: '再次确认：清空全部数据？',
                  content: '数据将被永久删除且无法恢复，请确认已做好备份。',
                  okText: '确认清空',
                  cancelText: '取消',
                  okButtonProps: { danger: true },
                  onOk: () => handleClearAllApi(),
                });
              }}
              okText="继续"
              cancelText="取消"
              okButtonProps={{ danger: true }}
            >
              <Button danger icon={<DeleteOutlined />}>
                清空所有数据
              </Button>
            </Popconfirm>
          </>
        )}

        <Divider />

        <Button type="link" onClick={resetSettings} style={{ padding: 0 }}>
          恢复默认设置
        </Button>
      </Card>

      <Card title="数据备份与恢复" style={{ maxWidth: 600, marginBottom: 16 }}>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
          导出全量数据（分类与快照）为 JSON 备份文件；导入时可选合并或覆盖模式。
        </Typography.Paragraph>
        <Space>
          <Button
            icon={<DownloadOutlined />}
            onClick={() => void handleExportAll()}
          >
            导出全部数据
          </Button>
          <Button
            icon={<UploadOutlined />}
            onClick={() => fileInputRef.current?.click()}
          >
            导入备份
          </Button>
          <input
            ref={fileInputRef}
            type="file"
            accept=".json,application/json"
            style={{ display: 'none' }}
            onChange={(e) => void handleBackupFileChange(e)}
          />
        </Space>
      </Card>

      <Card
        title="数据库信息"
        style={{ maxWidth: 600 }}
        extra={
          <Button
            type="link"
            size="small"
            icon={<ReloadOutlined />}
            onClick={() => void refreshSysInfo()}
          >
            刷新
          </Button>
        }
      >
        {sysInfoLoading && !sysInfo ? (
          <Spin />
        ) : sysInfo ? (
          <Descriptions column={1} size="small">
            <Descriptions.Item label="数据模式">
              {USE_MOCK ? 'Mock（浏览器 localStorage）' : '真实 API（服务端数据库）'}
            </Descriptions.Item>
            <Descriptions.Item label="数据库路径">
              <Typography.Text style={{ wordBreak: 'break-all' }}>
                {sysInfo.dbPath}
              </Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="分类数量">
              {sysInfo.categoryCount}
            </Descriptions.Item>
            <Descriptions.Item label="快照记录数">
              {sysInfo.snapshotRowCount}
            </Descriptions.Item>
            <Descriptions.Item label="最近快照日期">
              {latestDate ?? '暂无'}
            </Descriptions.Item>
          </Descriptions>
        ) : (
          <Typography.Text type="secondary">暂无数据库信息</Typography.Text>
        )}
      </Card>

      <Modal
        title="恢复备份"
        open={restorePending !== null}
        onOk={() => void handleConfirmRestore()}
        onCancel={() => setRestorePending(null)}
        okText="开始恢复"
        cancelText="取消"
        confirmLoading={restoring}
      >
        {restorePending && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Typography.Paragraph style={{ marginBottom: 0 }}>
              备份包含 {restorePending.backup.categories.length} 个分类、
              {restorePending.valid.length} 条有效快照
              {restorePending.skipped.length > 0
                ? `，另有 ${restorePending.skipped.length} 条快照因格式问题被跳过`
                : ''}。
            </Typography.Paragraph>
            <Radio.Group
              value={restoreMode}
              onChange={(e) =>
                setRestoreMode(e.target.value as RestoreMode)
              }
            >
              <Space direction="vertical">
                <Radio value="merge">
                  合并：保留现有数据，已存在的快照日期跳过
                </Radio>
                <Radio value="overwrite">
                  覆盖：清空现有全部数据后导入备份
                </Radio>
              </Space>
            </Radio.Group>
            {restoreMode === 'overwrite' && (
              <Alert
                type="warning"
                showIcon
                message="覆盖模式将删除当前所有分类与快照数据，且不可恢复"
              />
            )}
          </Space>
        )}
      </Modal>
    </>
  );
};

export default Settings;
