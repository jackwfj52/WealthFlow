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
 * - 批量创建：表格逐行填写，已有快照的日期经确认后直接覆盖
 */
import React, { useMemo, useState, useCallback, useEffect } from 'react';
import {
  Table,
  Button,
  Drawer,
  Form,
  DatePicker,
  Input,
  InputNumber,
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
  PlusSquareOutlined,
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
import GrowthBadge from '../../components/GrowthBadge';
import { useSnapshots, useCategories } from '../../app/storage';
import { useSettings } from '../../app/settings';
import { snapshotService, categoryService } from '../../services';
import { isValidAmount } from '../../utils/amount';
import { isValidDateOnly, isValidDateRange } from '../../utils/date';
import { getCategoryColor } from '../../utils/color';
import { calcGrowthRate, bandColorOf } from '../../utils/rate';
import type { AssetSnapshot, SnapshotItem } from '../../types/domain';
import type { SnapshotBatchEntry } from '../../services/types';
import {
  parseImportJson,
  buildExportJson,
  downloadTextFile,
  runImport,
  IMPORT_TEMPLATE,
} from './importExport';
import type { ParseReport, ImportRunReport, SkippedEntry } from './importExport';

dayjs.extend(customParseFormat);

/** 批量创建表格中的一行：一个日期 + 各分类金额 */
type BatchRow = {
  key: number;
  date: dayjs.Dayjs | null;
  amounts: Record<string, string | undefined>;
};

const Snapshots: React.FC = () => {
  const { snapshots, loading, refresh: refreshSnapshots } = useSnapshots();
  const { categories, refresh: refreshCategories } = useCategories();
  const { settings } = useSettings();

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

  // 分页
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

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
    result.sort((a, b) =>
      settings.snapshotDateOrder === 'asc'
        ? a.snapshotDate.localeCompare(b.snapshotDate)
        : b.snapshotDate.localeCompare(a.snapshotDate)
    );
    return result;
  }, [snapshots, filterDateRange, filterCategory, settings.snapshotDateOrder]);

  // 每个快照日期对应的前一个快照（按日期升序的全量数据，不受筛选影响）
  const prevByDate = useMemo(() => {
    const sorted = [...snapshots].sort((a, b) => a.snapshotDate.localeCompare(b.snapshotDate));
    const map = new Map<string, AssetSnapshot>();
    for (let i = 1; i < sorted.length; i++) {
      map.set(sorted[i].snapshotDate, sorted[i - 1]);
    }
    return map;
  }, [snapshots]);

  // 数据变化后修正页码（如批量删除后当前页超出范围）
  useEffect(() => {
    const maxPage = Math.max(1, Math.ceil(filteredSnapshots.length / pageSize));
    if (page > maxPage) setPage(maxPage);
  }, [filteredSnapshots.length, page, pageSize]);

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
        setPage(1);
      } else {
        setFilterDateRange(null);
        setPage(1);
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

  // --- 批量创建 ---
  const [batchOpen, setBatchOpen] = useState(false);
  const [batchSaving, setBatchSaving] = useState(false);
  const [batchRows, setBatchRows] = useState<BatchRow[]>([]);

  const createEmptyBatchRow = useCallback(
    (): BatchRow => ({ key: Date.now() + Math.random(), date: null, amounts: {} }),
    []
  );

  const openBatchDrawer = useCallback(() => {
    setBatchRows([createEmptyBatchRow()]);
    setBatchOpen(true);
  }, [createEmptyBatchRow]);

  const closeBatchDrawer = useCallback(() => {
    setBatchOpen(false);
    setBatchRows([]);
  }, []);

  const updateBatchRowDate = useCallback(
    (key: number, date: dayjs.Dayjs | null) => {
      setBatchRows((rows) =>
        rows.map((row) => (row.key === key ? { ...row, date } : row))
      );
    },
    []
  );

  const updateBatchRowAmount = useCallback(
    (key: number, categoryId: string, value: string | null) => {
      setBatchRows((rows) =>
        rows.map((row) =>
          row.key === key
            ? { ...row, amounts: { ...row.amounts, [categoryId]: value ?? undefined } }
            : row
        )
      );
    },
    []
  );

  const removeBatchRow = useCallback((key: number) => {
    setBatchRows((rows) =>
      rows.length <= 1 ? rows : rows.filter((row) => row.key !== key)
    );
  }, []);

  const addBatchRow = useCallback(() => {
    setBatchRows((rows) => [...rows, createEmptyBatchRow()]);
  }, [createEmptyBatchRow]);

  const handleBatchSubmit = useCallback(async () => {
    if (batchRows.length === 0) {
      message.error('请至少添加一行快照数据');
      return;
    }

    const entries: SnapshotBatchEntry[] = [];
    const seenDates = new Set<string>();

    for (const row of batchRows) {
      if (!row.date) {
        message.error('每行的快照日期都不能为空');
        return;
      }

      const dateStr = row.date.format('YYYY-MM-DD');
      if (!isValidDateOnly(dateStr)) {
        message.error(`日期"${dateStr}"无效，请选择真实日期且不能晚于今天`);
        return;
      }
      if (seenDates.has(dateStr)) {
        message.error(`日期"${dateStr}"重复，同一批次中不能有重复日期`);
        return;
      }
      seenDates.add(dateStr);

      const filledItems = categories
        .filter((category) => {
          const raw = row.amounts[category.id];
          return raw !== undefined && String(raw).trim() !== '';
        })
        .map((category) => ({
          categoryId: category.id,
          categoryName: category.name,
          amount: String(row.amounts[category.id]).trim(),
        }));

      if (filledItems.length === 0) {
        message.error(`日期 ${dateStr} 至少需要填写一个分类的金额`);
        return;
      }
      for (const item of filledItems) {
        if (!isValidAmount(item.amount)) {
          message.error(
            `日期 ${dateStr} 分类"${item.categoryName}"金额无效，需为大于 0 且最多两位小数`
          );
          return;
        }
      }

      entries.push({ snapshotDate: dateStr, items: filledItems });
    }

    const overwriteDates = entries
      .map((entry) => entry.snapshotDate)
      .filter((date) => snapshots.some((snapshot) => snapshot.snapshotDate === date));

    const doSave = async () => {
      setBatchSaving(true);
      try {
        await snapshotService.batchSave(entries);
        message.success(
          overwriteDates.length > 0
            ? `已批量保存 ${entries.length} 个日期的快照，覆盖 ${overwriteDates.length} 个已有日期`
            : `已批量创建 ${entries.length} 个日期的快照`
        );
        setBatchOpen(false);
        setBatchRows([]);
        refreshSnapshots();
      } catch (err) {
        if (err instanceof Error) {
          message.error(err.message);
        }
      } finally {
        setBatchSaving(false);
      }
    };

    if (overwriteDates.length > 0) {
      Modal.confirm({
        title: '覆盖确认',
        content: (
          <>
            <p>以下日期的快照已存在，提交后将直接覆盖：</p>
            <ul style={{ marginBottom: 0 }}>
              {overwriteDates.map((date) => (
                <li key={date}>{date}</li>
              ))}
            </ul>
            <p style={{ marginTop: 8 }}>覆盖后原数据不可恢复，是否继续？</p>
          </>
        ),
        okText: '确认覆盖',
        okButtonProps: { danger: true },
        cancelText: '取消',
        onOk: () => doSave(),
      });
    } else {
      await doSave();
    }
  }, [batchRows, categories, snapshots, refreshSnapshots]);

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

  // --- 表格列定义：日期 + 总资产 + 每个分类一列 ---
  const columns: ColumnsType<AssetSnapshot> = [
    {
      title: '日期',
      dataIndex: 'snapshotDate',
      key: 'snapshotDate',
      width: 140,
      fixed: 'left',
      sorter: (a, b) => a.snapshotDate.localeCompare(b.snapshotDate),
      render: (v: string) => <Tag color="blue">{v}</Tag>,
    },
    {
      title: '总资产',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 200,
      render: (v: string, record: AssetSnapshot) => {
        const prev = prevByDate.get(record.snapshotDate);
        const rate = prev ? calcGrowthRate(v, prev.totalAmount) : null;
        return (
          <Space size={6}>
            {rate !== null && (
              <GrowthBadge rate={rate} color={bandColorOf(rate, settings.rateColors)} />
            )}
            <AmountText amount={v} style={{ fontWeight: 500 }} />
          </Space>
        );
      },
    },
    ...categories.map((category, idx) => ({
      title: (
        <Space size={6}>
          <span
            style={{
              display: 'inline-block',
              width: 10,
              height: 10,
              borderRadius: '50%',
              background: getCategoryColor(category, idx),
            }}
          />
          {category.name}
        </Space>
      ),
      key: category.id,
      width: 180,
      align: 'left' as const,
      render: (_: unknown, record: AssetSnapshot) => {
        const item = record.items.find((i) => i.categoryId === category.id);
        if (!item) {
          return <Typography.Text type="secondary">—</Typography.Text>;
        }
        const prev = prevByDate.get(record.snapshotDate);
        const prevItem = prev?.items.find((i) => i.categoryId === category.id);
        const rate = prevItem ? calcGrowthRate(item.amount, prevItem.amount) : null;
        return (
          <Space size={6}>
            {rate !== null && (
              <GrowthBadge rate={rate} color={bandColorOf(rate, settings.rateColors)} />
            )}
            <AmountText amount={item.amount} />
          </Space>
        );
      },
    })),
    {
      title: '操作',
      key: 'actions',
      width: 180,
      fixed: 'right',
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

  const categoryOptions = categories.map((c, idx) => ({
    label: (
      <Space size={6}>
        <span
          style={{
            display: 'inline-block',
            width: 10,
            height: 10,
            borderRadius: '50%',
            background: getCategoryColor(c, idx),
          }}
        />
        {c.name}
      </Space>
    ),
    value: c.id,
  }));

  // --- 批量创建表格列：日期 + 每个分类一列 ---
  const batchColumns: ColumnsType<BatchRow> = [
    {
      title: '快照日期',
      key: 'date',
      width: 150,
      fixed: 'left',
      render: (_, row) => (
        <DatePicker
          style={{ width: '100%' }}
          value={row.date}
          disabledDate={(d) => d && d.isAfter(dayjs(), 'day')}
          placeholder="选择日期"
          onChange={(date) => updateBatchRowDate(row.key, date)}
        />
      ),
    },
    ...categories.map((category) => ({
      title: category.name,
      key: category.id,
      width: 130,
      render: (_: unknown, row: BatchRow) => (
        <InputNumber
          style={{ width: '100%' }}
          min="0"
          precision={2}
          stringMode
          placeholder="金额（元）"
          value={row.amounts[category.id] ?? null}
          onChange={(value) => updateBatchRowAmount(row.key, category.id, value)}
        />
      ),
    })),
    {
      title: '操作',
      key: 'actions',
      width: 80,
      fixed: 'right',
      render: (_, row) => (
        <Button
          type="text"
          danger
          icon={<DeleteOutlined />}
          disabled={batchRows.length <= 1}
          onClick={() => removeBatchRow(row.key)}
        >
          移除
        </Button>
      ),
    },
  ];

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
            <Button icon={<DownloadOutlined />} onClick={openImport}>
              导入 JSON
            </Button>
            <Button icon={<UploadOutlined />} onClick={handleExport}>
              导出 JSON
            </Button>
            <Button icon={<PlusSquareOutlined />} onClick={openBatchDrawer}>
              批量创建
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
          onChange={(v) => {
            setFilterCategory(v);
            setPage(1);
          }}
        />
        <Button
          icon={<SearchOutlined />}
          onClick={() => {
            setFilterDateRange(null);
            setFilterCategory(undefined);
            setPage(1);
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
          scroll={{ x: 'max-content' }}
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys as string[]),
          }}
          pagination={{
            current: page,
            pageSize,
            pageSizeOptions: [10, 20, 50, 100],
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
            onChange: (p, ps) => {
              setPage(p);
              if (ps && ps !== pageSize) {
                setPageSize(ps);
                setPage(1);
              }
            },
          }}
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

      {/* 批量创建抽屉 */}
      <Drawer
        title="批量创建快照"
        open={batchOpen}
        onClose={closeBatchDrawer}
        width={960}
        extra={
          <Space>
            <Button onClick={closeBatchDrawer}>取消</Button>
            <Button type="primary" loading={batchSaving} onClick={handleBatchSubmit}>
              批量保存
            </Button>
          </Space>
        }
      >
        <Alert
          title="表格中每一行对应一个日期的快照，各分类金额按列填写。已存在快照的日期将在你确认后直接覆盖，覆盖后不可恢复。"
          type="info"
          showIcon
          style={{ marginBottom: 20 }}
        />

        {categories.length === 0 ? (
          <Alert
            showIcon
            type="warning"
            message="暂无分类"
            description="请先在「分类管理」页面创建分类，再回来批量创建。"
          />
        ) : (
          <>
            <Table<BatchRow>
              rowKey="key"
              columns={batchColumns}
              dataSource={batchRows}
              pagination={false}
              scroll={{ x: 'max-content' }}
              size="small"
            />
            <Button
              type="dashed"
              block
              icon={<PlusOutlined />}
              style={{ marginTop: 12 }}
              disabled={batchRows.length >= 100}
              onClick={addBatchRow}
            >
              添加一行（最多 100 行）
            </Button>
          </>
        )}
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
