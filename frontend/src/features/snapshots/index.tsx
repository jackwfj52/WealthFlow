/**
 * 资产快照页面
 *
 * 核心业务规则：
 * - 每日资产更新 = 新增快照，不覆盖历史数据
 * - 新增时若日期已有快照 → 提示用户"编辑当天快照"或"取消"
 * - 编辑快照只影响指定日期，不波及其他日期
 * - 同一日期同一分类最多一条明细
 * - 删除需二次确认
 * - 日期严格校验 YYYY-MM-DD 真实日期，不能晚于今天
 */
import React, { useMemo, useState, useCallback } from 'react';
import {
  Table,
  Button,
  Drawer,
  Form,
  DatePicker,
  Input,
  Select,
  Space,
  Popconfirm,
  message,
  Tag,
  Divider,
  Typography,
  Alert,
  Modal,
  Spin,
  Upload,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  SearchOutlined,
  UploadOutlined,
  DownloadOutlined,
  InboxOutlined,
  CheckSquareOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';
import PageHeader from '../../components/PageHeader';
import AmountText from '../../components/AmountText';
import EmptyState from '../../components/EmptyState';
import { useSnapshots, useCategories } from '../../app/storage';
import { snapshotService, categoryService } from '../../services';
import { isValidAmount } from '../../utils/amount';
import { isValidDateOnly, isValidDateRange } from '../../utils/date';
import type { AssetSnapshot, SnapshotItem } from '../../types/domain';
import {
  parseImportJson,
  buildExportJson,
  downloadTextFile,
  runImport,
  IMPORT_TEMPLATE,
} from './importExport';
import type { ParseReport, ImportRunReport, SkippedEntry } from './importExport';

dayjs.extend(customParseFormat);

const Snapshots: React.FC = () => {
  const { snapshots, loading, refresh: refreshSnapshots } = useSnapshots();
  const { categories, refresh: refreshCategories } = useCategories();

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<'add' | 'edit'>('add');
  const [editingSnapshot, setEditingSnapshot] = useState<AssetSnapshot | null>(null);
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  // 导入导出
  const [importOpen, setImportOpen] = useState(false);
  const [importParsed, setImportParsed] = useState<ParseReport | null>(null);
  const [importError, setImportError] = useState<string | null>(null);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<ImportRunReport | null>(null);
  const [uploadKey, setUploadKey] = useState(0);

  // 筛选
  const [filterDateRange, setFilterDateRange] = useState<[string, string] | null>(null);
  const [filterCategory, setFilterCategory] = useState<string | undefined>(undefined);

  // 批量删除
  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);
  const [batchDeleting, setBatchDeleting] = useState(false);

  // --- 过滤后的数据 ---
  const filteredSnapshots = useMemo(() => {
    let result = [...snapshots];
    if (filterDateRange) {
      result = result.filter(
        (s) => s.snapshotDate >= filterDateRange[0] && s.snapshotDate <= filterDateRange[1]
      );
    }
    if (filterCategory) {
      result = result.filter((s) => s.items.some((i) => i.categoryId === filterCategory));
    }
    result.sort((a, b) => b.snapshotDate.localeCompare(a.snapshotDate));
    return result;
  }, [snapshots, filterDateRange, filterCategory]);

  // --- 打开新增抽屉 ---
  const openAddDrawer = useCallback(() => {
    setDrawerMode('add');
    setEditingSnapshot(null);
    form.resetFields();
    setDrawerOpen(true);
  }, [form]);

  // --- 打开编辑抽屉 ---
  const openEditDrawer = useCallback(
    (record: AssetSnapshot) => {
      setDrawerMode('edit');
      setEditingSnapshot(record);
      // 编辑只影响当前日期的数据，不波及其他日期
      form.setFieldsValue({
        snapshotDate: dayjs(record.snapshotDate, 'YYYY-MM-DD', true),
        items: record.items.map((item) => ({
          categoryId: item.categoryId,
          amount: item.amount,
        })),
      });
      setDrawerOpen(true);
    },
    [form]
  );

  // --- 删除快照 ---
  const handleDelete = useCallback(
    async (id: string) => {
      await snapshotService.delete(id);
      message.success('快照已删除');
      refreshSnapshots();
    },
    [refreshSnapshots]
  );

  // --- 提交表单（新增/编辑） ---
  const handleSubmit = useCallback(async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);

      // dayjs DatePicker 的值保证是有效日期，使用严格格式化输出 YYYY-MM-DD
      const dateStr = (values.snapshotDate as dayjs.Dayjs).format('YYYY-MM-DD');

      // 二次校验：日期不能晚于今天（即使 DatePicker 已限制，但作为防御性校验）
      if (!isValidDateOnly(dateStr)) {
        message.error('日期无效，请选择真实日期且不能晚于今天');
        return;
      }

      // 构建明细，过滤掉金额为空的分类项
      const rawItems = values.items as { categoryId: string; amount: string }[];
      const nonEmptyItems = rawItems.filter((item) => item.amount && item.amount.trim());

      if (nonEmptyItems.length === 0) {
        message.error('至少需要填写一个分类的金额');
        return;
      }

      const items: SnapshotItem[] = nonEmptyItems.map((item) => {
        const cat = categories.find((c) => c.id === item.categoryId);
        return {
          categoryId: item.categoryId,
          categoryName: cat?.name ?? '',
          amount: item.amount.trim(),
        };
      });

      // 同一快照内分类去重检查
      const catIds = items.map((i) => i.categoryId);
      const dupCat = catIds.find((id, idx) => catIds.indexOf(id) !== idx);
      if (dupCat) {
        const dupName = categories.find((c) => c.id === dupCat)?.name ?? dupCat;
        message.error(`分类"${dupName}"重复，同一日期同一分类只能有一条明细`);
        return;
      }

      if (drawerMode === 'add') {
        const exists = await snapshotService.existsByDate(dateStr);
        if (exists) {
          const existing = await snapshotService.getByDate(dateStr);
          Modal.confirm({
            title: '该日期已有快照',
            content: `日期 ${dateStr} 已有快照数据。新增每日快照不会覆盖历史数据。是否跳转到编辑当天快照？`,
            okText: '去编辑',
            cancelText: '取消',
            onOk: () => {
              if (existing) {
                setDrawerMode('edit');
                setEditingSnapshot(existing);
                form.setFieldsValue({
                  snapshotDate: dayjs(existing.snapshotDate, 'YYYY-MM-DD', true),
                  items: existing.items.map((item) => ({
                    categoryId: item.categoryId,
                    amount: item.amount,
                  })),
                });
              }
            },
          });
          return;
        }
        await snapshotService.create(dateStr, items);
        message.success('快照已新增');
      } else {
        if (!editingSnapshot) return;
        await snapshotService.update(editingSnapshot.id, items);
        message.success('快照已更新');
      }

      setDrawerOpen(false);
      form.resetFields();
      refreshSnapshots();
    } catch (err) {
      if (err instanceof Error) {
        message.error(err.message);
      }
    } finally {
      setSaving(false);
    }
  }, [form, drawerMode, editingSnapshot, categories, refreshSnapshots]);

  // --- 日期范围筛选变更 ---
  const handleDateRangeChange = useCallback(
    (dates: [dayjs.Dayjs | null, dayjs.Dayjs | null] | null) => {
      if (dates && dates[0] && dates[1]) {
        const start = dates[0].format('YYYY-MM-DD');
        const end = dates[1].format('YYYY-MM-DD');
        const range = isValidDateRange(start, end);
        if (!range.valid) {
          message.warning(range.error ?? '日期范围无效');
          return;
        }
        setFilterDateRange([start, end]);
      } else {
        setFilterDateRange(null);
      }
    },
    []
  );

  // --- 批量删除 ---
  const handleBatchDelete = useCallback(async () => {
    if (selectedRowKeys.length === 0) return;
    setBatchDeleting(true);
    let success = 0;
    let failed = 0;
    for (const id of selectedRowKeys) {
      try {
        await snapshotService.delete(id);
        success += 1;
      } catch {
        failed += 1;
      }
    }
    setSelectedRowKeys([]);
    refreshSnapshots();
    if (failed === 0) {
      message.success(`已删除 ${success} 条快照`);
    } else {
      message.warning(`删除完成：成功 ${success} 条，失败 ${failed} 条`);
    }
    setBatchDeleting(false);
  }, [selectedRowKeys, refreshSnapshots]);

  // --- 导入导出 ---
  const openImport = useCallback(() => {
    setImportOpen(true);
    setImportParsed(null);
    setImportError(null);
    setImportResult(null);
  }, []);

  const closeImport = useCallback(() => {
    setImportOpen(false);
    setImportParsed(null);
    setImportError(null);
    setImportResult(null);
  }, []);

  const resetImportSelection = useCallback(() => {
    setImportParsed(null);
    setImportError(null);
    setImportResult(null);
    setUploadKey((k) => k + 1);
  }, []);

  const handleImportFile = useCallback(async (file: File) => {
    const text = await file.text();
    const result = parseImportJson(text);
    setImportResult(null);
    if (result.ok) {
      setImportParsed(result.report);
      setImportError(null);
    } else {
      setImportParsed(null);
      setImportError(result.error);
    }
    return false;
  }, []);

  const handleRunImport = useCallback(async () => {
    if (!importParsed || importParsed.valid.length === 0) return;
    setImporting(true);
    try {
      const report = await runImport(importParsed.valid, categoryService, snapshotService);
      setImportResult(report);
      refreshSnapshots();
      refreshCategories();
    } catch (err) {
      message.error(`导入中断：${err instanceof Error ? err.message : '未知错误'}`);
    } finally {
      setImporting(false);
    }
  }, [importParsed, refreshSnapshots, refreshCategories]);

  const handleExport = useCallback(() => {
    if (snapshots.length === 0) {
      message.warning('暂无快照数据可导出');
      return;
    }
    const json = buildExportJson(snapshots);
    downloadTextFile(`wealthflow-snapshots-${dayjs().format('YYYY-MM-DD')}.json`, json);
    message.success(`已导出 ${snapshots.length} 条快照`);
  }, [snapshots]);

  const renderSkippedList = (list: SkippedEntry[]) => (
    <div style={{ maxHeight: 160, overflow: 'auto' }}>
      {list.map((s, index) => (
        <div key={index}>
          {s.date ? `${s.date}：` : ''}
          {s.reason}
        </div>
      ))}
    </div>
  );

  // --- 表格列定义 ---
  const columns: ColumnsType<AssetSnapshot> = [
    {
      title: '日期',
      dataIndex: 'snapshotDate',
      key: 'snapshotDate',
      width: 140,
      sorter: (a, b) => a.snapshotDate.localeCompare(b.snapshotDate),
      render: (v: string) => <Tag color="blue">{v}</Tag>,
    },
    {
      title: '总资产',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      render: (v: string) => <AmountText amount={v} style={{ fontWeight: 500 }} />,
    },
    {
      title: '分类明细数',
      key: 'itemCount',
      render: (_, record) => `${record.items.length} 个分类`,
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => openEditDrawer(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除"
            description={`确定要删除 ${record.snapshotDate} 的快照吗？此操作不可撤销。`}
            onConfirm={() => handleDelete(record.id)}
            okText="确认删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const categoryOptions = categories.map((c) => ({ label: c.name, value: c.id }));

  if (loading) {
    return <Spin size="large" style={{ display: 'block', marginTop: 120 }} />;
  }

  return (
    <>
      <PageHeader
        title="资产快照"
        subtitle="每日资产更新按新增快照处理，不会覆盖历史数据"
        extra={
          <Space>
            <Button icon={<UploadOutlined />} onClick={openImport}>
              导入 JSON
            </Button>
            <Button icon={<DownloadOutlined />} onClick={handleExport}>
              导出 JSON
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openAddDrawer}>
              新增快照
            </Button>
          </Space>
        }
      />

      {/* 筛选栏 */}
      <Space wrap style={{ marginBottom: 16 }}>
        <DatePicker.RangePicker
          placeholder={['开始日期', '结束日期']}
          onChange={handleDateRangeChange}
          allowClear
        />
        <Select
          placeholder="按分类筛选"
          allowClear
          style={{ width: 160 }}
          options={categoryOptions}
          value={filterCategory}
          onChange={(v) => setFilterCategory(v)}
        />
        <Button
          icon={<SearchOutlined />}
          onClick={() => {
            setFilterDateRange(null);
            setFilterCategory(undefined);
          }}
        >
          重置筛选
        </Button>
        <Button
          icon={<CheckSquareOutlined />}
          disabled={filteredSnapshots.length === 0}
          onClick={() => setSelectedRowKeys(filteredSnapshots.map((s) => s.id))}
        >
          全选
        </Button>
        <Popconfirm
          title="批量删除"
          description={`确定要删除选中的 ${selectedRowKeys.length} 条快照吗？此操作不可撤销。`}
          onConfirm={handleBatchDelete}
          okText="确认删除"
          cancelText="取消"
          okButtonProps={{ danger: true }}
          disabled={selectedRowKeys.length === 0}
        >
          <Button
            danger
            icon={<DeleteOutlined />}
            loading={batchDeleting}
            disabled={selectedRowKeys.length === 0}
          >
            批量删除（{selectedRowKeys.length}）
          </Button>
        </Popconfirm>
        {selectedRowKeys.length > 0 && (
          <Button type="link" onClick={() => setSelectedRowKeys([])}>
            取消选择
          </Button>
        )}
      </Space>

      {snapshots.length === 0 ? (
        <EmptyState
          description="还没有资产快照，添加第一条开始记录吧"
          actionLabel="新增第一条快照"
          onAction={openAddDrawer}
        />
      ) : (
        <Table
          dataSource={filteredSnapshots}
          columns={columns}
          rowKey="id"
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys as string[]),
          }}
          pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条快照` }}
        />
      )}

      {/* 新增 / 编辑抽屉 */}
      <Drawer
        title={drawerMode === 'add' ? '新增快照' : `编辑快照 — ${editingSnapshot?.snapshotDate ?? ''}`}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          form.resetFields();
        }}
        size="large"
        extra={
          <Space>
            <Button
              onClick={() => {
                setDrawerOpen(false);
                form.resetFields();
              }}
            >
              取消
            </Button>
            <Button type="primary" loading={saving} onClick={handleSubmit}>
              {drawerMode === 'add' ? '新增' : '保存编辑'}
            </Button>
          </Space>
        }
      >
        <Alert
          title={
            drawerMode === 'add'
              ? '每日资产更新按新增快照处理。若所选日期已有快照，系统将提示您编辑而非覆盖。'
              : '编辑仅修改当前日期的数据，不会影响其他日期的快照。'
          }
          type="info"
          showIcon
          style={{ marginBottom: 20 }}
        />

        <Form form={form} layout="vertical">
          <Form.Item
            name="snapshotDate"
            label="快照日期"
            rules={[{ required: true, message: '请选择快照日期' }]}
          >
            <DatePicker
              style={{ width: '100%' }}
              disabled={drawerMode === 'edit'}
              disabledDate={(d) => d && d.isAfter(dayjs(), 'day')}
              placeholder="请选择日期（不能晚于今天）"
            />
          </Form.Item>

          <Divider plain>分类金额明细</Divider>
          <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
            为每个分类录入金额（单位：元），无需填写的分类可留空（至少填写一个）
          </Typography.Text>

          <Form.List name="items" initialValue={categories.map((c) => ({ categoryId: c.id, amount: '' }))}>
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...rest }) => (
                  <Space key={key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
                    <Form.Item
                      {...rest}
                      name={[name, 'categoryId']}
                      rules={[{ required: true, message: '请选择分类' }]}
                      style={{ width: 160 }}
                    >
                      <Select placeholder="选择分类" options={categoryOptions} />
                    </Form.Item>
                    <Form.Item
                      {...rest}
                      name={[name, 'amount']}
                      rules={[
                        {
                          validator: (_, value) => {
                            if (!value) return Promise.resolve();
                            if (!isValidAmount(value)) {
                              return Promise.reject(new Error('请输入有效正数金额'));
                            }
                            return Promise.resolve();
                          },
                        },
                      ]}
                      style={{ flex: 1 }}
                    >
                      <Input placeholder="金额（元）" />
                    </Form.Item>
                    <Button
                      type="text"
                      danger
                      onClick={() => remove(name)}
                      disabled={fields.length <= 1}
                    >
                      移除
                    </Button>
                  </Space>
                ))}
                <Button
                  type="dashed"
                  onClick={() => add({ categoryId: '', amount: '' })}
                  block
                >
                  添加分类
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Drawer>

      {/* 导入 JSON 弹窗 */}
      <Modal
        title="导入快照 JSON"
        open={importOpen}
        onCancel={closeImport}
        width={640}
        footer={
          importResult
            ? [
                <Button key="close" type="primary" onClick={closeImport}>
                  关闭
                </Button>,
              ]
            : importParsed
              ? [
                  <Button key="back" onClick={resetImportSelection}>
                    重新选择文件
                  </Button>,
                  <Button
                    key="run"
                    type="primary"
                    loading={importing}
                    disabled={importParsed.valid.length === 0}
                    onClick={handleRunImport}
                  >
                    开始导入（{importParsed.valid.length} 条）
                  </Button>,
                ]
              : [
                  <Button key="cancel" onClick={closeImport}>
                    取消
                  </Button>,
                ]
        }
      >
        {importResult ? (
          <>
            <Alert
              type="success"
              showIcon
              message={`导入完成：成功 ${importResult.created} 条快照${
                importResult.createdCategories > 0
                  ? `，自动创建分类 ${importResult.createdCategories} 个`
                  : ''
              }`}
            />
            {importResult.skipped.length > 0 && (
              <Alert
                type="warning"
                showIcon
                style={{ marginTop: 12 }}
                message={`跳过 ${importResult.skipped.length} 条`}
                description={renderSkippedList(importResult.skipped)}
              />
            )}
          </>
        ) : importParsed ? (
          <>
            <Alert
              type="info"
              showIcon
              message={`解析成功：将导入 ${importParsed.valid.length} 条快照`}
            />
            {importParsed.skipped.length > 0 && (
              <Alert
                type="warning"
                showIcon
                style={{ marginTop: 12 }}
                message={`${importParsed.skipped.length} 条将被跳过`}
                description={renderSkippedList(importParsed.skipped)}
              />
            )}
          </>
        ) : (
          <>
            {importError && (
              <Alert type="error" showIcon message={importError} style={{ marginBottom: 16 }} />
            )}
            <Upload.Dragger
              key={uploadKey}
              accept=".json,application/json"
              multiple={false}
              showUploadList={false}
              beforeUpload={handleImportFile}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">点击或拖拽 JSON 文件到此处</p>
              <p className="ant-upload-hint">文件格式见下方说明，可下载模板作为转换参考</p>
            </Upload.Dragger>

            <Typography.Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 8 }}>
              标准格式（导出即此格式）：
            </Typography.Paragraph>
            <pre
              style={{
                background: '#f6f6f6',
                padding: 12,
                borderRadius: 6,
                fontSize: 12,
                maxHeight: 200,
                overflow: 'auto',
              }}
            >
              {IMPORT_TEMPLATE}
            </pre>
            <Typography.Paragraph type="secondary">
              规则：日期为真实日期且不晚于今天；金额为正数、最多两位小数；同一日期同一分类只能一条；
              分类按名称自动匹配，不存在则自动创建；已存在的日期整条跳过（不覆盖历史数据）。
            </Typography.Paragraph>
            <Button
              size="small"
              icon={<DownloadOutlined />}
              onClick={() => downloadTextFile('wealthflow-import-template.json', IMPORT_TEMPLATE)}
            >
              下载模板文件
            </Button>
          </>
        )}
      </Modal>
    </>
  );
};

export default Snapshots;
