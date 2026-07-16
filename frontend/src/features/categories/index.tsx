/**
 * 分类管理页面
 *
 * 规则：
 * - 支持新增、编辑、删除分类
 * - 已被快照使用的分类不能直接删除
 * - 分类名称不能重复、不能为纯空格
 * - 重命名分类时会同步更新历史快照中的名称
 */
import React, { useMemo, useState, useCallback } from 'react';
import {
  Table,
  Button,
  Modal,
  Form,
  Input,
  Space,
  Popconfirm,
  message,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '../../components/PageHeader';
import EmptyState from '../../components/EmptyState';
import { useCategories, useSnapshots } from '../../app/storage';
import { categoryService } from '../../services/mockCategories';
import type { AssetCategory } from '../../types/domain';

const Categories: React.FC = () => {
  const { categories, refresh: refreshCategories } = useCategories();
  const { snapshots, refresh: refreshSnapshots } = useSnapshots();

  const [modalOpen, setModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState<'add' | 'edit'>('add');
  const [editingCategory, setEditingCategory] = useState<AssetCategory | null>(null);
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  const categoryUsageCount = useMemo(() => {
    const counts = new Map<string, number>();
    snapshots.forEach((s) => {
      const catIds = new Set(s.items.map((i) => i.categoryId));
      catIds.forEach((id) => {
        counts.set(id, (counts.get(id) ?? 0) + 1);
      });
    });
    return counts;
  }, [snapshots]);

  const openAddModal = useCallback(() => {
    setModalMode('add');
    setEditingCategory(null);
    form.resetFields();
    setModalOpen(true);
  }, [form]);

  const openEditModal = useCallback(
    (record: AssetCategory) => {
      setModalMode('edit');
      setEditingCategory(record);
      form.setFieldsValue({ name: record.name });
      setModalOpen(true);
    },
    [form]
  );

  const handleSubmit = useCallback(async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const name: string = values.name;

      // 前端层面的纯空格校验
      if (!name.trim()) {
        message.error('分类名称不能为空或纯空格');
        return;
      }

      if (modalMode === 'add') {
        categoryService.create(name);
        message.success('分类已新增');
      } else {
        if (editingCategory) {
          categoryService.update(editingCategory.id, name);
          message.success('分类已更新');
          // 重命名可能影响了快照中的 categoryName，同步刷新
          refreshSnapshots();
        }
      }
      setModalOpen(false);
      form.resetFields();
      refreshCategories();
    } catch (err) {
      if (err instanceof Error) {
        message.error(err.message);
      }
    } finally {
      setSaving(false);
    }
  }, [form, modalMode, editingCategory, refreshCategories, refreshSnapshots]);

  const handleDelete = useCallback(
    (id: string, name: string) => {
      const usage = categoryUsageCount.get(id) ?? 0;
      if (usage > 0) {
        message.warning(`分类"${name}"已被 ${usage} 条快照使用，无法删除`);
        return;
      }
      categoryService.delete(id);
      message.success('分类已删除');
      refreshCategories();
    },
    [categoryUsageCount, refreshCategories]
  );

  const columns: ColumnsType<AssetCategory> = [
    { title: '分类名称', dataIndex: 'name', key: 'name' },
    { title: '创建日期', dataIndex: 'createdAt', key: 'createdAt', width: 140 },
    {
      title: '快照使用次数',
      key: 'usage',
      width: 140,
      render: (_, record) => categoryUsageCount.get(record.id) ?? 0,
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (_, record) => {
        const inUse = (categoryUsageCount.get(record.id) ?? 0) > 0;
        return (
          <Space>
            <Button
              type="link"
              icon={<EditOutlined />}
              onClick={() => openEditModal(record)}
            >
              编辑
            </Button>
            {inUse ? (
              <Button type="link" disabled icon={<DeleteOutlined />}>
                使用中
              </Button>
            ) : (
              <Popconfirm
                title="确认删除"
                description={`确定要删除分类"${record.name}"吗？`}
                onConfirm={() => handleDelete(record.id, record.name)}
                okText="确认删除"
                cancelText="取消"
                okButtonProps={{ danger: true }}
              >
                <Button type="link" danger icon={<DeleteOutlined />}>
                  删除
                </Button>
              </Popconfirm>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <>
      <PageHeader
        title="分类管理"
        subtitle="管理资产分类，已被快照使用的分类不能删除"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openAddModal}>
            新增分类
          </Button>
        }
      />

      {categories.length === 0 ? (
        <EmptyState
          description="还没有分类，请先添加资产分类"
          actionLabel="新增分类"
          onAction={openAddModal}
        />
      ) : (
        <Table
          dataSource={categories}
          columns={columns}
          rowKey="id"
          pagination={false}
        />
      )}

      <Modal
        title={modalMode === 'add' ? '新增分类' : '编辑分类'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
        }}
        confirmLoading={saving}
        okText={modalMode === 'add' ? '新增' : '保存'}
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="分类名称"
            rules={[
              { required: true, message: '请输入分类名称' },
              { max: 20, message: '分类名称不超过20个字符' },
              {
                validator: (_, value) => {
                  if (value && !value.trim()) {
                    return Promise.reject(new Error('分类名称不能为纯空格'));
                  }
                  return Promise.resolve();
                },
              },
            ]}
          >
            <Input placeholder="请输入分类名称" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

export default Categories;
