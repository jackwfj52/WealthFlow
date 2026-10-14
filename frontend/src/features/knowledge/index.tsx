import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert, Button, Card, Drawer, Empty, Form, Input, List, Modal, Popconfirm,
  Select, Space, Spin, Switch, Table, Tabs, Tag, Typography, message,
} from 'antd';
import { DeleteOutlined, DownloadOutlined, EditOutlined, PlusOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useSearchParams } from 'react-router-dom';
import PageHeader from '../../components/PageHeader';
import { knowledgeService } from '../../services/apiKnowledge';
import type {
  KnowledgeDocument, KnowledgePage, KnowledgePayload, KnowledgeSource,
  KnowledgeStatus, KnowledgeType, KnowledgeVersion, KnowledgeSearchResult,
} from '../../services/apiKnowledge';
import ImportDialog, { statusOptions, typeOptions } from './ImportDialog';
import DocumentBody from './DocumentBody';

const typeLabel = (type: KnowledgeType) => typeOptions.find((option) => option.value === type)?.label ?? type;
const statusLabel = (status: KnowledgeStatus) => statusOptions.find((option) => option.value === status)?.label ?? status;
const statusColor = (status: KnowledgeStatus) => status === 'ACTIVE' ? 'green' : status === 'ARCHIVED' ? 'default' : 'gold';
const dateLabel = (date: string) => date ? date.replace('T', ' ').slice(0, 19) : '—';
const EMPTY_PAGE: KnowledgePage = { items: [], total: 0, page: 0, pageSize: 20 };

const downloadOriginal = (item: Pick<KnowledgeDocument, 'title' | 'body' | 'format' | 'version'>) => {
  const extension = item.format === 'MARKDOWN' ? '.md' : '.txt';
  let name = item.title.replace(/[<>:"/\\|?*\u0000-\u001F]/g, '_').replace(/[.\s]+$/g, '').trim() || '投资资料';
  if (/^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)/i.test(name)) name = `资料_${name}`;
  const blob = new Blob([item.body], { type: item.format === 'MARKDOWN' ? 'text/markdown;charset=utf-8' : 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = window.document.createElement('a');
  anchor.href = url; anchor.download = `${name.slice(0, 120)}-v${item.version}${extension}`;
  window.document.body.appendChild(anchor); anchor.click(); anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
};

const Knowledge: React.FC = () => {
  const [params, setParams] = useSearchParams();
  const selectedId = params.get('documentId');
  const selectedChunkId = params.get('chunkId');
  const sourceVersion = params.get('version');
  const [tab, setTab] = useState('documents');
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [filterType, setFilterType] = useState<KnowledgeType>();
  const [filterStatus, setFilterStatus] = useState<KnowledgeStatus>();
  const [data, setData] = useState<KnowledgePage>(EMPTY_PAGE);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const listSequence = useRef(0);
  const [importOpen, setImportOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<KnowledgeDocument | null>(null);
  const [form] = Form.useForm<KnowledgePayload>();
  const [saving, setSaving] = useState(false);
  const [document, setDocument] = useState<KnowledgeDocument | null>(null);
  const [documentLoading, setDocumentLoading] = useState(false);
  const [documentError, setDocumentError] = useState('');
  const [versions, setVersions] = useState<KnowledgeVersion[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [versionsError, setVersionsError] = useState('');
  const [selectedVersion, setSelectedVersion] = useState<KnowledgeVersion | null>(null);
  const [raw, setRaw] = useState(false);
  const [statusSaving, setStatusSaving] = useState(false);
  const [source, setSource] = useState<KnowledgeSource | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchType, setSearchType] = useState<KnowledgeType>();
  const [includeArchived, setIncludeArchived] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchResult, setSearchResult] = useState<KnowledgeSearchResult | null>(null);
  const [searchError, setSearchError] = useState('');
  const editBody = Form.useWatch('body', form);
  const editFormat = Form.useWatch('format', form);

  useEffect(() => { if (selectedChunkId) setRaw(true); }, [selectedChunkId]);

  const reload = useCallback(() => setRefresh((value) => value + 1), []);

  useEffect(() => {
    const sequence = ++listSequence.current;
    setLoading(true);
    setLoadError('');
    knowledgeService.list({ query: query.trim(), type: filterType, status: filterStatus, page })
      .then((result) => { if (sequence === listSequence.current) setData(result); })
      .catch((error) => { if (sequence === listSequence.current) setLoadError(error instanceof Error ? error.message : '知识库加载失败'); })
      .finally(() => { if (sequence === listSequence.current) setLoading(false); });
    return () => { if (sequence === listSequence.current) listSequence.current += 1; };
  }, [query, filterType, filterStatus, page, refresh]);

  useEffect(() => {
    if (!selectedId) { setDocument(null); return; }
    let disposed = false;
    setDocumentLoading(true); setDocument(null); setDocumentError(''); setVersions([]); setVersionsError(''); setVersionsLoading(true);
    knowledgeService.get(selectedId)
      .then((item) => { if (!disposed) setDocument(item); })
      .catch((error) => { if (!disposed) setDocumentError(error instanceof Error ? error.message : '资料加载失败，来源可能已删除'); })
      .finally(() => { if (!disposed) setDocumentLoading(false); });
    knowledgeService.versions(selectedId)
      .then((items) => { if (!disposed) setVersions(items); })
      .catch((error) => { if (!disposed) setVersionsError(error instanceof Error ? error.message : '版本记录加载失败'); })
      .finally(() => { if (!disposed) setVersionsLoading(false); });
    return () => { disposed = true; };
  }, [selectedId, refresh]);

  const openDocument = (id: string, reference?: KnowledgeSource) => {
    setSource(reference ?? null); setRaw(Boolean(reference));
    const next = new URLSearchParams(params);
    next.set('documentId', id);
    if (reference) { next.set('chunkId', reference.id); next.set('version', String(reference.version)); }
    else { next.delete('chunkId'); next.delete('version'); }
    setParams(next);
  };

  const closeDocument = () => {
    const next = new URLSearchParams(params);
    next.delete('documentId'); next.delete('chunkId'); next.delete('version');
    setParams(next); setSource(null); setSelectedVersion(null);
  };

  const openEditor = (item?: KnowledgeDocument) => {
    setEditing(item ?? null); form.resetFields();
    form.setFieldsValue(item ? {
      title: item.title, body: item.body, tags: item.tags ?? [], format: item.format, type: item.type, status: item.status,
    } : { title: '', body: '', tags: [], type: 'STRATEGY', format: 'MARKDOWN', status: 'DRAFT' });
    setEditOpen(true);
  };

  const saveDocument = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const payload: KnowledgePayload = {
        ...values, title: values.title.trim(),
        tags: [...new Set((values.tags ?? []).map((tag) => tag.trim()).filter(Boolean))],
        ...(editing ? { expectedVersion: editing.version } : {}),
      };
      const saved = editing ? await knowledgeService.update(editing.id, payload) : await knowledgeService.create(payload);
      message.success(editing ? '资料已保存，新版本已记录' : '资料已创建');
      setEditOpen(false); reload(); openDocument(saved.id);
    } catch (error) {
      if (error instanceof Error) message.error(error.message);
    } finally {
      setSaving(false);
    }
  };

  const changeStatus = async (item: KnowledgeDocument, status: KnowledgeStatus) => {
    setStatusSaving(true);
    try {
      await knowledgeService.setStatus(item.id, status, item.version);
      message.success(`资料已${statusLabel(status)}`); reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '状态更新失败');
    } finally {
      setStatusSaving(false);
    }
  };

  const deleteDocument = async (item: KnowledgeDocument) => {
    try {
      await knowledgeService.remove(item.id);
      message.success('资料及索引已删除');
      if (selectedId === item.id) closeDocument();
      setSearchResult(null); reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const runSearch = async () => {
    if (!searchQuery.trim()) { message.warning('请输入问题或关键词'); return; }
    setSearchLoading(true); setSearchResult(null); setSearchError('');
    try {
      setSearchResult(await knowledgeService.search(searchQuery.trim(), { type: searchType, limit: 12, includeArchived }));
    } catch (error) {
      setSearchError(error instanceof Error ? error.message : '检索失败');
    } finally {
      setSearchLoading(false);
    }
  };

  const columns: ColumnsType<KnowledgeDocument> = [
    { title: '资料标题', dataIndex: 'title', key: 'title', render: (_, item) => (
      <div><Button type="link" style={{ padding: 0, height: 'auto', whiteSpace: 'normal', textAlign: 'left' }} onClick={() => openDocument(item.id)}>{item.title}</Button>
        <div>{(item.tags ?? []).map((tag) => <Tag key={tag}>{tag}</Tag>)}</div></div>
    ) },
    { title: '类型', dataIndex: 'type', width: 110, render: (type: KnowledgeType) => typeLabel(type) },
    { title: '状态', dataIndex: 'status', width: 90, render: (status: KnowledgeStatus) => <Tag color={statusColor(status)}>{statusLabel(status)}</Tag> },
    { title: '版本', dataIndex: 'version', width: 75, render: (version: number) => `v${version}` },
    { title: '语义索引', key: 'index', width: 160, render: (_, item) => (
      <Space orientation="vertical" size={0}>
        <Typography.Text type="secondary">{item.indexedCount ?? 0} / {item.chunkCount ?? 0} 段</Typography.Text>
        {(item.pendingCount ?? 0) > 0 && <Typography.Text type="secondary">待处理 {item.pendingCount} 段</Typography.Text>}
        {(item.failedCount ?? 0) > 0 && <Typography.Text type="warning">失败 {item.failedCount} 段</Typography.Text>}
      </Space>
    ) },
    { title: '更新时间', dataIndex: 'updatedAt', width: 170, render: dateLabel },
    { title: '操作', key: 'actions', width: 160, render: (_, item) => (
      <Space size={0}>
        <Button type="link" icon={<EditOutlined />} onClick={() => openEditor(item)}>编辑</Button>
        <Popconfirm title={`删除「${item.title}」？`} description="原文、历史版本和检索索引将一并删除。" okText="删除" cancelText="取消" okButtonProps={{ danger: true }} onConfirm={() => deleteDocument(item)}>
          <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
        </Popconfirm>
      </Space>
    ) },
  ];

  const currentSource = source?.documentId === document?.id ? source : null;
  const referenceVersion = currentSource?.version ?? (sourceVersion ? Number(sourceVersion) : undefined);
  const referencedVersion = Number.isFinite(referenceVersion) && referenceVersion !== document?.version;
  const historicalSource = referencedVersion ? versions.find((version) => version.version === referenceVersion) : undefined;
  const referencedChunk = !referencedVersion ? document?.chunks?.find((chunk) => chunk.id === selectedChunkId) : undefined;

  return (
    <>
      <PageHeader title="投资知识库" subtitle="保存投资策略、复盘与研究资料，让助手在回答时参考你的投资思路。"
        extra={<Space wrap>
          <Button icon={<ReloadOutlined />} onClick={reload} loading={loading}>刷新</Button>
          <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>导入文本 / Markdown</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor()}>新增资料</Button>
        </Space>} />
      <Tabs activeKey={tab} onChange={setTab} items={[
        { key: 'documents', label: '资料管理', children: <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          <Space wrap>
            <Input.Search placeholder="搜索标题、标签或正文关键词" allowClear maxLength={200} style={{ width: 300 }} onSearch={(value) => { setQuery(value); setPage(0); }} />
            <Select allowClear placeholder="全部类型" value={filterType} options={typeOptions} style={{ width: 140 }} onChange={(value) => { setFilterType(value); setPage(0); }} />
            <Select allowClear placeholder="全部状态" value={filterStatus} options={statusOptions} style={{ width: 120 }} onChange={(value) => { setFilterStatus(value); setPage(0); }} />
            <Typography.Text type="secondary">共 {data.total} 份资料</Typography.Text>
          </Space>
          {loadError && <Alert type="error" showIcon title={loadError} action={<Button size="small" onClick={reload}>重试</Button>} />}
          <Table columns={columns} dataSource={data.items} rowKey="id" loading={loading} scroll={{ x: 1000 }}
            locale={{ emptyText: <Empty description={query || filterType || filterStatus ? '没有符合条件的资料' : '导入或新建你的第一份投资策略'} /> }}
            pagination={{ current: page + 1, total: data.total, pageSize: data.pageSize, showSizeChanger: false, onChange: (value) => setPage(value - 1) }} />
        </Space> },
        { key: 'search', label: '知识检索', children: <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Text type="secondary">输入问题或关键词，搜索生效资料。可包含归档资料用于复盘；草稿仅在资料管理中查看。配置 Embedding 后会结合语义和关键词检索。</Typography.Text>
          <Input.Search value={searchQuery} onChange={(event) => setSearchQuery(event.target.value)} onSearch={() => void runSearch()}
            placeholder="例如：市场下跌时，我的纳指定投策略是什么？" enterButton="检索资料" loading={searchLoading} maxLength={2000} />
          <Space wrap>
            <Select allowClear value={searchType} onChange={setSearchType} options={typeOptions} placeholder="全部类型" style={{ width: 150 }} />
            <Switch checked={includeArchived} onChange={setIncludeArchived} />
            <Typography.Text>包含归档资料（用于历史复盘）</Typography.Text>
          </Space>
          {searchError && <Alert type="error" showIcon title={searchError} />}
          {searchResult?.warning && <Alert type="warning" showIcon title={searchResult.warning} />}
          {searchResult && <Typography.Text type="secondary">
            本次检索：{/HYBRID/i.test(searchResult.mode) ? '关键词 + 语义' : /VECTOR|SEMANTIC/i.test(searchResult.mode) ? '语义检索' : '关键词检索'} · {searchResult.results.length} 条结果
          </Typography.Text>}
          <List loading={searchLoading} dataSource={searchResult?.results ?? []}
            locale={{ emptyText: <Empty description={searchResult ? '没有找到相关资料，请换一种说法或确认策略已生效' : '输入问题，查找你的投资策略与笔记'} /> }}
            renderItem={(item) => <List.Item><Card style={{ width: '100%' }}>
              <Space orientation="vertical" style={{ width: '100%' }}>
                <Space wrap>
                  <Button type="link" style={{ padding: 0, height: 'auto', whiteSpace: 'normal' }} onClick={() => openDocument(item.documentId, item)}>{item.title}</Button>
                  <Tag>{typeLabel(item.type)}</Tag><Tag color={statusColor(item.status)}>{statusLabel(item.status)}</Tag>
                  <Typography.Text type="secondary">v{item.version} · {dateLabel(item.updatedAt)}</Typography.Text>
                </Space>
                {item.section && <Typography.Text strong>{item.section}</Typography.Text>}
                <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{item.excerpt}</Typography.Paragraph>
                <Button size="small" onClick={() => openDocument(item.documentId, item)}>打开原文</Button>
              </Space>
            </Card></List.Item>} />
        </Space> },
      ]} />

      <ImportDialog open={importOpen} onClose={() => setImportOpen(false)} onImported={reload} />

      <Modal title={editing ? '编辑资料' : '新增投资资料'} width={900} open={editOpen} onOk={saveDocument} confirmLoading={saving}
        onCancel={() => { if (!saving) setEditOpen(false); }} okText="保存" cancelText="取消" maskClosable={!saving}>
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true, whitespace: true, message: '请输入资料标题' }, { max: 120, message: '标题不超过 120 个字符' }]}><Input maxLength={120} /></Form.Item>
          <Space wrap align="start">
            <Form.Item name="type" label="类型" rules={[{ required: true }]}><Select options={typeOptions} style={{ width: 160 }} /></Form.Item>
            <Form.Item name="format" label="正文格式" rules={[{ required: true }]}><Select options={[{ value: 'MARKDOWN', label: 'Markdown' }, { value: 'TEXT', label: '纯文本' }]} style={{ width: 140 }} /></Form.Item>
            <Form.Item name="status" label="状态" rules={[{ required: true }]}><Select options={statusOptions} style={{ width: 120 }} /></Form.Item>
          </Space>
          <Form.Item name="tags" label="标签" extra="最多 20 个标签，每个最多 40 个字符" rules={[{
            validator: (_, values: string[] | undefined) => {
              const tags = [...new Set((values ?? []).map((value) => value.trim()).filter(Boolean))];
              return tags.length > 20 || tags.some((tag) => tag.length > 40)
                ? Promise.reject(new Error('最多 20 个标签，每个最多 40 个字符')) : Promise.resolve();
            },
          }]}><Select mode="tags" maxCount={20} tokenSeparators={[',', '，']} placeholder="例如：纳指、定投、资产配置" /></Form.Item>
          <Tabs items={[
            { key: 'edit', label: '编辑原文', forceRender: true, children: <Form.Item name="body" rules={[{ required: true, whitespace: true, message: '正文不能为空' }, { max: 200000, message: '正文不能超过 20 万字符' }, {
              validator: (_, value: string | undefined) => value?.includes('\0') || value?.includes('\uFFFD')
                ? Promise.reject(new Error('正文含不可保存的控制字符或替代符号')) : Promise.resolve(),
            }]}><Input.TextArea rows={14} placeholder="粘贴或编写你的投资思路。策略的金额规则、适用条件与暂停条件都可写在这里。" style={{ fontFamily: 'monospace' }} /></Form.Item> },
            { key: 'preview', label: '阅读预览', children: <div style={{ maxHeight: 450, overflow: 'auto', padding: 12 }}><DocumentBody body={editBody ?? ''} format={editFormat ?? 'TEXT'} /></div> },
          ]} />
          <Typography.Text type="secondary">保存修改会保留历史版本。助手使用当前生效策略，草稿与归档资料不会作为现行规则。</Typography.Text>
        </Form>
      </Modal>

      <Drawer title={historicalSource?.title ?? document?.title ?? '资料详情'} width={850} open={Boolean(selectedId)} onClose={closeDocument}>
        {documentLoading && <Spin style={{ display: 'block', marginTop: 60 }} />}
        {documentError && <Alert type="error" showIcon title={documentError} description="请刷新重试；已删除的来源无法打开。" action={<Button onClick={reload}>重试</Button>} />}
        {document && <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          <Space wrap><Tag>{typeLabel(document.type)}</Tag><Tag color={statusColor(document.status)}>{statusLabel(document.status)}</Tag><Typography.Text type="secondary">v{document.version} · 更新于 {dateLabel(document.updatedAt)}</Typography.Text></Space>
          <Space wrap>{(document.tags ?? []).map((tag) => <Tag key={tag}>{tag}</Tag>)}</Space>
          {document.sourceName && <Typography.Text type="secondary">导入文件：{document.sourceName}</Typography.Text>}
          <Space wrap>
            <Button icon={<DownloadOutlined />} disabled={referencedVersion && !historicalSource} onClick={() => downloadOriginal(historicalSource ?? document)}>{referencedVersion ? '下载引用版本' : '下载原文'}</Button>
            <Button icon={<EditOutlined />} onClick={() => openEditor(document)}>编辑原文</Button>
            {document.status !== 'ACTIVE' && <Popconfirm title="将这份资料设为生效？" description={document.type === 'STRATEGY' ? '助手会将它作为当前投资策略参考。' : undefined} okText="设为生效" cancelText="取消" onConfirm={() => changeStatus(document, 'ACTIVE')}><Button type="primary" loading={statusSaving}>设为生效</Button></Popconfirm>}
            {document.status === 'ACTIVE' && <Button loading={statusSaving} onClick={() => changeStatus(document, 'DRAFT')}>转为草稿</Button>}
            {document.status !== 'ARCHIVED' && <Popconfirm title="归档这份资料？" description="归档后不参与当前决策检索，仍可用于历史复盘。" okText="归档" cancelText="取消" onConfirm={() => changeStatus(document, 'ARCHIVED')}><Button loading={statusSaving}>归档</Button></Popconfirm>}
          </Space>
          {selectedChunkId && <Alert type={referencedChunk || currentSource || historicalSource ? 'info' : 'warning'} showIcon title={referencedChunk?.section || currentSource?.section ? `引用段落：${referencedChunk?.section ?? currentSource?.section}` : '已打开引用来源'}
            description={<div>{currentSource && <div style={{ whiteSpace: 'pre-wrap' }}>{currentSource.excerpt}</div>}<Typography.Text type="secondary">{referencedVersion ? '下方展示引用当时的历史版本正文。' : referencedChunk ? '已在下方原文中标出引用段落。' : '当前原文中未找到该引用段落，资料可能已经更新，请查看历史版本。'} 来源 ID：{selectedChunkId}</Typography.Text></div>} />}
          {referencedVersion && <Alert type="warning" showIcon title={`这条引用来自 v${referenceVersion}，当前资料为 v${document.version}。下方为历史原文，状态操作仍作用于当前资料。`} />}
          <Tabs items={[
            { key: 'body', label: '原文', children: <>
              {(historicalSource?.format ?? document.format) === 'MARKDOWN' && <Button size="small" style={{ marginBottom: 16 }} onClick={() => setRaw(!raw)}>{raw ? '阅读预览' : '查看 Markdown 原文'}</Button>}
              {referencedVersion ? historicalSource
                ? <DocumentBody body={historicalSource.body} format={historicalSource.format} raw={raw} focusText={currentSource?.excerpt && historicalSource.body.includes(currentSource.excerpt) ? currentSource.excerpt : undefined} />
                : <Alert type="warning" showIcon title={versionsError || (versionsLoading ? `正在查找引用版本 v${referenceVersion}…` : `未找到引用版本 v${referenceVersion}，无法展示当时原文。`)} />
                : <DocumentBody body={document.body} format={document.format} raw={raw} focusText={referencedChunk?.content} />}
            </> },
            { key: 'versions', label: `历史版本 (${versions.length})`, children: <>
              {versionsError && <Alert type="error" title={versionsError} action={<Button size="small" onClick={reload}>重试</Button>} />}
              <List loading={versionsLoading} dataSource={versions} locale={{ emptyText: '尚无版本记录' }} renderItem={(version) => <List.Item actions={[<Button key="view" onClick={() => setSelectedVersion(version)}>查看原文</Button>]}>
                <List.Item.Meta title={<Space><Typography.Text strong>v{version.version} · {version.title}</Typography.Text><Tag color={statusColor(version.status)}>{statusLabel(version.status)}</Tag></Space>} description={dateLabel(version.createdAt)} />
              </List.Item>} />
            </> },
          ]} />
        </Space>}
      </Drawer>

      <Modal title={selectedVersion ? `${selectedVersion.title} · v${selectedVersion.version}` : '历史版本'} open={Boolean(selectedVersion)} width={850}
        onCancel={() => setSelectedVersion(null)} footer={<Button onClick={() => setSelectedVersion(null)}>关闭</Button>}>
        {selectedVersion && <>
          <Alert type="info" title="这是历史版本，仅供核对与复盘。" style={{ marginBottom: 16 }} />
          <div style={{ maxHeight: '65vh', overflow: 'auto' }}><DocumentBody body={selectedVersion.body} format={selectedVersion.format} /></div>
        </>}
      </Modal>
    </>
  );
};

export default Knowledge;
