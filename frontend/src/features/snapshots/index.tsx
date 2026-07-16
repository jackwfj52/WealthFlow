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
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';
import PageHeader from '../../components/PageHeader';
import AmountText from '../../components/AmountText';
import EmptyState from '../../components/EmptyState';
import { useSnapshots, useCategories } from '../../app/storage';
import { snapshotService } from '../../services/mockSnapshots';
import { isValidAmount } from '../../utils/amount';
import { isValidDateOnly, isValidDateRange } from '../../utils/date';
import type { AssetSnapshot, SnapshotItem } from '../../types/domain';

dayjs.extend(customParseFormat);

const Snapshots: React.FC = () => {
  const { snapshots, refresh: refreshSnapshots } = useSnapshots();
  const { categories } = useCategories();

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<'add' | 'edit'>('add');
  const [editingSnapshot, setEditingSnapshot] = useState<AssetSnapshot | null>(null);
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  // 筛选
  const [filterDateRange, setFilterDateRange] = useState<[string, string] | null>(null);
  const [filterCategory, setFilterCategory] = useState<string | undefined>(undefined);

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
    (id: string) => {
      snapshotService.delete(id);
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
        if (snapshotService.existsByDate(dateStr)) {
          Modal.confirm({
            title: '该日期已有快照',
            content: `日期 ${dateStr} 已有快照数据。新增每日快照不会覆盖历史数据。是否跳转到编辑当天快照？`,
            okText: '去编辑',
            cancelText: '取消',
            onOk: () => {
              const existing = snapshotService.getByDate(dateStr);
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
        snapshotService.create(dateStr, items);
        message.success('快照已新增');
      } else {
        if (!editingSnapshot) return;
        snapshotService.update(editingSnapshot.id, items);
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

  return (
    <>
      <PageHeader
        title="资产快照"
        subtitle="每日资产更新按新增快照处理，不会覆盖历史数据"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openAddDrawer}>
            新增快照
          </Button>
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
    </>
  );
};

export default Snapshots;
