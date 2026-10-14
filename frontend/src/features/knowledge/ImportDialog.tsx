import React, { useRef, useState } from 'react';
import { Alert, Button, Input, Modal, Select, Space, Tabs, Typography, message } from 'antd';
import { DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import { knowledgeService } from '../../services/apiKnowledge';
import type { KnowledgeFormat, KnowledgeStatus, KnowledgeType } from '../../services/apiKnowledge';
import DocumentBody from './DocumentBody';

interface ImportFile {
  key: string;
  sourceName: string;
  title: string;
  format: KnowledgeFormat;
  body: string;
}

export const typeOptions = [
  { value: 'STRATEGY', label: '投资策略' },
  { value: 'INVESTMENT_REVIEW', label: '投资复盘' },
  { value: 'RESEARCH', label: '研究资料' },
  { value: 'GENERAL_NOTE', label: '普通笔记' },
];
export const statusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'ACTIVE', label: '生效' },
  { value: 'ARCHIVED', label: '归档' },
];

const ImportDialog: React.FC<{ open: boolean; onClose: () => void; onImported: () => void }> = ({ open, onClose, onImported }) => {
  const fileInput = useRef<HTMLInputElement>(null);
  const [files, setFiles] = useState<ImportFile[]>([]);
  const [type, setType] = useState<KnowledgeType>('STRATEGY');
  const [status, setStatus] = useState<KnowledgeStatus>('DRAFT');
  const [tags, setTags] = useState<string[]>([]);
  const [activeKey, setActiveKey] = useState<string>();
  const [reading, setReading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const [raw, setRaw] = useState(false);

  const readFiles = async (selected: FileList | null) => {
    if (!selected?.length) return;
    const selectedFiles = Array.from(selected);
    if (files.length + selectedFiles.length > 20) {
      message.error('每次最多导入 20 个文件');
      return;
    }
    setReading(true);
    setErrors([]);
    try {
      const outcomes = await Promise.all(selectedFiles.map(async (file) => {
        try {
          if (!/\.(txt|md)$/i.test(file.name)) throw new Error('只支持 .txt 和 .md 文件');
          if (file.name.length > 180) throw new Error('文件名不能超过 180 个字符，请先重命名');
          if (file.size > 1024 * 1024) throw new Error('文件过大，单份正文不能超过 20 万字符');
          const body = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer());
          if (!body.trim()) throw new Error('正文不能为空');
          if (body.length > 200000) throw new Error('单份正文不能超过 20 万字符');
          if (body.includes('\0') || body.includes('\uFFFD')) throw new Error('正文含不可保存的控制字符或替代符号');
          const item: ImportFile = {
            key: crypto.randomUUID(), sourceName: file.name,
            title: file.name.replace(/\.(txt|md)$/i, '').slice(0, 120),
            format: /\.md$/i.test(file.name) ? 'MARKDOWN' : 'TEXT', body,
          };
          return { item };
        } catch (error) {
          return { error: `${file.name}：${error instanceof TypeError ? '文件不是有效的 UTF-8 文本' : error instanceof Error ? error.message : '读取失败'}` };
        }
      }));
      const additions = outcomes.flatMap((result) => result.item ? [result.item] : []);
      if (files.reduce((sum, item) => sum + item.body.length, 0) + additions.reduce((sum, item) => sum + item.body.length, 0) > 2000000) {
        setErrors(['本批次正文总量不能超过 200 万字符，请减少文件数量']);
        return;
      }
      setFiles((current) => [...current, ...additions]);
      setErrors(outcomes.flatMap((result) => result.error ? [result.error] : []));
      if (!activeKey && additions.length) setActiveKey(additions[0].key);
    } finally {
      setReading(false);
      if (fileInput.current) fileInput.current.value = '';
    }
  };

  const reset = () => {
    setFiles([]); setErrors([]); setActiveKey(undefined);
    setType('STRATEGY'); setStatus('DRAFT'); setTags([]); setRaw(false);
    if (fileInput.current) fileInput.current.value = '';
  };

  const submit = async () => {
    if (!files.length || files.some((file) => !file.title.trim())) {
      message.error('请选择文件，并为每份资料填写标题');
      return;
    }
    if (files.some((file) => file.title.trim().length > 120)) {
      message.error('资料标题不能超过 120 个字符');
      return;
    }
    const normalizedTags = [...new Set(tags.map((tag) => tag.trim()).filter(Boolean))];
    if (normalizedTags.length > 20 || normalizedTags.some((tag) => tag.length > 40)) {
      message.error('最多 20 个标签，每个最多 40 个字符');
      return;
    }
    setSaving(true);
    try {
      const result = await knowledgeService.importDocuments(files.map((file) => ({
        sourceName: file.sourceName, title: file.title.trim(), format: file.format,
        body: file.body, tags: normalizedTags, type, status,
      })));
      if (result.created.length) message.success(`已导入 ${result.created.length} 份资料`);
      onImported();
      if (result.skipped.length) {
        setErrors(result.skipped.map((name) => `已跳过：${name}`));
        setFiles([]);
      } else {
        onClose();
        reset();
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '导入失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="导入投资资料" open={open} width={900}
      onCancel={() => { if (!saving && !reading) { onClose(); reset(); } }}
      onOk={submit} confirmLoading={saving} okText={`导入 ${files.length} 份资料`}
      okButtonProps={{ disabled: !files.length || reading }} cancelText="取消" maskClosable={!saving && !reading}>
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <Typography.Text type="secondary">支持 UTF-8 编码的 .txt / .md；可选择多个文件，导入前核对原文和标题。每次最多 20 份，单份最多 20 万字符，整批最多 200 万字符。</Typography.Text>
        <input ref={fileInput} type="file" accept=".txt,.md" multiple hidden onChange={(event) => void readFiles(event.target.files)} />
        <Button icon={<UploadOutlined />} loading={reading} disabled={saving} onClick={() => fileInput.current?.click()}>选择文件</Button>
        {errors.length > 0 && <Alert type="warning" showIcon title="部分文件需要处理" description={<div>{errors.map((error, index) => <div key={index}>{error}</div>)}</div>} />}
        <Space wrap>
          <Typography.Text>资料类型</Typography.Text>
          <Select value={type} onChange={setType} options={typeOptions} style={{ width: 140 }} disabled={saving} />
          <Typography.Text>状态</Typography.Text>
          <Select value={status} onChange={setStatus} options={statusOptions} style={{ width: 100 }} disabled={saving} />
        </Space>
        <Select mode="tags" maxCount={20} value={tags} onChange={(values: string[]) => {
          if (values.some((value) => value.trim().length > 40)) { message.error('每个标签最多 40 个字符'); return; }
          setTags(values);
        }} style={{ width: '100%' }} placeholder="标签（最多 20 项，每项 40 字），例如：纳指、定投、资产配置" tokenSeparators={[',', '，']} disabled={saving} />
        {status === 'ACTIVE' && type === 'STRATEGY' && <Alert type="info" showIcon title="导入后，助手会将这些资料作为当前生效的个人投资策略参考。" />}
        {files.length > 0 && <Tabs activeKey={activeKey} onChange={setActiveKey} items={files.map((file) => ({
          key: file.key, label: file.sourceName,
          children: <Space orientation="vertical" style={{ width: '100%' }}>
            <Space style={{ display: 'flex' }}>
              <Input value={file.title} maxLength={120} aria-label="导入资料标题" placeholder="资料标题（最多 120 字）" disabled={saving}
                onChange={(event) => setFiles((current) => current.map((item) => item.key === file.key ? { ...item, title: event.target.value } : item))} />
              <Button danger icon={<DeleteOutlined />} disabled={saving || reading} onClick={() => {
                const remaining = files.filter((item) => item.key !== file.key);
                setFiles(remaining); setActiveKey(remaining[0]?.key);
              }}>移除</Button>
              {file.format === 'MARKDOWN' && <Button onClick={() => setRaw(!raw)}>{raw ? '阅读预览' : '查看原文'}</Button>}
            </Space>
            <div style={{ maxHeight: 350, overflow: 'auto', padding: 12 }}><DocumentBody body={file.body} format={file.format} raw={raw} /></div>
          </Space>,
        }))} />}
      </Space>
    </Modal>
  );
};

export default ImportDialog;
