import { useRef, useState } from 'react';
import { Alert, Button, Card, Modal, Space, Typography, message } from 'antd';
import { DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useSettings } from '../../app/settings';
import { apiClient } from '../../services/apiClient';
import { downloadTextFile } from '../snapshots/importExport';
import { parseFullBackup, type FullBackup } from './fullBackup';

export default function FullBackupCard() {
  const { settings, updateSettings } = useSettings();
  const input = useRef<HTMLInputElement>(null);
  const [pending, setPending] = useState<FullBackup | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  async function exportBackup() {
    setBusy(true);
    try {
      const data = await apiClient.get<Omit<FullBackup, 'settings'>>('/system/full-backup');
      downloadTextFile(`wealthflow-full-backup-${dayjs().format('YYYY-MM-DD-HHmmss')}.json`,
        JSON.stringify({ ...data, settings }, null, 2));
      message.success('完整备份已导出');
    } catch (error) { message.error(error instanceof Error ? error.message : '导出失败'); }
    finally { setBusy(false); }
  }

  async function restore() {
    if (!pending) return;
    setBusy(true);
    try {
      // 后端事务成功后才修改浏览器设置；失败保留当前界面和数据。
      await apiClient.post('/system/full-restore', pending);
      try {
        localStorage.setItem('wealthflow_settings', JSON.stringify(pending.settings));
        updateSettings(pending.settings);
      } catch {
        message.warning('服务端数据已恢复，但浏览器无法保存界面设置，请检查浏览器存储权限');
      }
      setPending(null);
      setDone(true);
    } catch (error) { message.error(error instanceof Error ? error.message : '恢复失败'); }
    finally { setBusy(false); }
  }

  return <>
    <Card title="完整备份与换机恢复" style={{ maxWidth: 600, marginBottom: 16 }}>
      <Typography.Paragraph type="secondary">
        包含分类、快照、具体持仓、知识库及版本、聊天记录、模型配置和当前浏览器的界面设置。
        API Key 不写入备份，恢复后需重新填写；语义索引需重新启用，待确认草案会取消，AI 权限恢复为审核模式。
      </Typography.Paragraph>
      <Space wrap>
        <Button icon={<DownloadOutlined />} loading={busy} onClick={() => void exportBackup()}>导出完整备份</Button>
        <Button icon={<UploadOutlined />} disabled={busy} onClick={() => input.current?.click()}>恢复完整备份</Button>
        <input aria-label="完整备份文件" ref={input} type="file" accept=".json,application/json" hidden
          onChange={async event => {
            const file = event.target.files?.[0];
            event.target.value = '';
            if (!file) return;
            try {
              if (file.size > 50 * 1024 * 1024) throw new Error('完整备份不能超过 50 MB');
              setPending(parseFullBackup(await file.text()));
            } catch (error) { message.error(error instanceof Error ? error.message : '文件读取失败'); }
          }} />
      </Space>
    </Card>
    <Modal title="覆盖恢复完整备份" open={pending !== null} confirmLoading={busy}
      closable={!busy} mask={{ closable: !busy }} cancelButtonProps={{ disabled: busy }}
      okText="确认覆盖恢复" okButtonProps={{ danger: true }} cancelText="取消"
      onCancel={() => setPending(null)} onOk={() => void restore()}>
      <Space orientation="vertical" style={{ width: '100%' }}>
        <Typography.Paragraph>
          备份日期：{pending?.exportedAt}。包含 {pending?.tables.asset_category?.length ?? 0} 个分类、
          {pending?.tables.knowledge_document?.length ?? 0} 篇知识文档、
          {pending?.tables.agent_conversation?.length ?? 0} 个会话。
        </Typography.Paragraph>
        <Alert type="warning" showIcon title="将覆盖现有数据与模型配置，请先导出当前完整备份。恢复期间请停止聊天和数据编辑，并关闭其他页面。" />
        <Typography.Text>恢复后重新填写 API Key；历史草案取消，AI 权限恢复为审核模式。</Typography.Text>
      </Space>
    </Modal>
    <Modal title="完整备份恢复成功" open={done} closable={false} mask={{ closable: false }} footer={
      <Button type="primary" onClick={() => window.location.reload()}>刷新并使用恢复的数据</Button>
    }>
      数据已恢复。请刷新页面，再到模型设置中重新填写 API Key，需要语义搜索时重新启用 Embedding。
    </Modal>
  </>;
}
