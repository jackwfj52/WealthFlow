import { useCallback, useEffect, useState } from 'react';
import {
  Alert, Button, Card, Col, Empty, Form, Input, InputNumber, Modal,
  Progress, Row, Select, Space, Spin, Table, Tag, Typography, message,
} from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import PageHeader from '../../components/PageHeader';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import { useApp } from '../../app/storage';
import { holdingService } from '../../services';
import type { AssetCategory, AssetHolding, HoldingInput } from '../../types/domain';
import { getCategoryColor } from '../../utils/color';
import { formatPercentage, totalPercentage, validateHoldings, SECTOR_OPTIONS, sectorLabel } from '../../utils/holdings';
import './index.css';

const { Text } = Typography;
const groupTitle = (name: string) => `${name}持仓`;
const examples = (name: string) => ({
  基金: '例如：纳斯达克100指数基金', 股票: '例如：英伟达', 理财: '例如：银行理财产品',
  贵金属: '例如：黄金、白银', 加密货币: '例如：BTC、ETH',
}[name] ?? '请输入具体标的名称');

export default function Holdings() {
  const { categories, loading: categoriesLoading } = useApp();
  const navigate = useNavigate();
  const [holdings, setHoldings] = useState<AssetHolding[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<AssetCategory | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<{ items: Partial<HoldingInput>[] }>();
  const items = Form.useWatch('items', form) as Partial<HoldingInput>[] | undefined;
  const total = totalPercentage(items ?? []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try { setHoldings(await holdingService.getAll()); }
    catch (err) { setError(err instanceof Error ? err.message : '持仓加载失败'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const openEditor = (category: AssetCategory) => {
    const current = holdings.filter(item => item.categoryId === category.id);
    form.resetFields();
    form.setFieldsValue({ items: current.length
      ? current.map(({ name, percentage, sector }) => ({ name, percentage, ...(sector ? { sector } : {}) }))
      : [{ name: '' }] });
    setEditing(category);
  };

  const save = async () => {
    if (!editing || saving) return;
    try {
      const values = await form.validateFields();
      const normalized = validateHoldings(values.items ?? []);
      setSaving(true);
      const saved = await holdingService.replace(editing.id, normalized);
      setHoldings(current => [...current.filter(item => item.categoryId !== editing.id), ...saved]);
      setEditing(null);
      message.success('持仓已保存');
    } catch (err) {
      if (err instanceof Error) message.error(err.message);
    } finally { setSaving(false); }
  };

  if (loading || categoriesLoading) return <Spin size="large" style={{ display: 'block', marginTop: 120 }} />;
  if (error) return <ErrorState message={error} onRetry={() => void load()} />;

  return <div className="holdings-page">
    <PageHeader title="具体持仓" subtitle="按资产类别记录具体标的，了解每一类资产内部的持仓结构"
      extra={<Link to="/categories"><Button>管理资产类别</Button></Link>} />
    {categories.length === 0 ? <EmptyState description="先添加资产类别，再登记该类别下的具体持仓"
      actionLabel="新增资产类别" onAction={() => navigate('/categories')} /> : <>
      <Alert type="info" showIcon title="占比按所属资产类别计算"
        description="例如纳斯达克占基金的 60%，表示类内占比。可为标的补充行业标签，供 AI 分析集中度；混合或宽基基金选择混合/宽基，未知时留空。每类合计最多 100%，剩余部分为未登记。"
        style={{ marginBottom: 20 }} />
      <Row gutter={[16, 16]}>
        {categories.map((category, index) => {
          const current = holdings.filter(item => item.categoryId === category.id);
          const registered = totalPercentage(current);
          const color = getCategoryColor(category, index);
          return <Col key={category.id} xs={24} xl={12}>
            <Card style={{ height: '100%' }} title={<Space size={10}>
              <span style={{ width: 10, height: 10, borderRadius: '50%', background: color, flexShrink: 0 }} />
              {groupTitle(category.name)}
            </Space>} extra={<Button type="link" icon={current.length ? <EditOutlined /> : <PlusOutlined />}
              aria-label={current.length ? '管理持仓' : '添加持仓'} onClick={() => openEditor(category)}>
                <span className="holdings-manage-label">{current.length ? '管理持仓' : '添加持仓'}</span>
              </Button>}>
              <Space wrap style={{ marginBottom: 12 }}>
                <Tag color={color}>{category.name}</Tag><Text type="secondary">{current.length} 个标的</Text>
                <Text type="secondary">已登记 {formatPercentage(registered)}</Text>
              </Space>
              <Progress percent={registered} showInfo={false} strokeColor={color} size="small" />
              {current.length ? <Table<AssetHolding> size="small" pagination={false} rowKey="id"
                dataSource={current} style={{ marginTop: 12 }} columns={[
                  { title: '标的名称 / 行业', dataIndex: 'name', render: (name: string, item) =>
                    <div style={{ overflowWrap: 'anywhere' }}><Text>{name}</Text>
                      <div><Text type="secondary" style={{ fontSize: 12 }}>{sectorLabel(item.sector)}</Text></div>
                    </div> },
                  { title: '类内占比', dataIndex: 'percentage', width: 96, align: 'right',
                    render: (value: number) => <Text strong style={{ fontVariantNumeric: 'tabular-nums' }}>{formatPercentage(value)}</Text> },
                ]} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={examples(category.name)}>
                  <Button onClick={() => openEditor(category)} icon={<PlusOutlined />}>登记第一个标的</Button>
                </Empty>}
              <div style={{ marginTop: 12, display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                <Text type="secondary">未登记</Text><Text type="secondary">{formatPercentage(100 - registered)}</Text>
              </div>
            </Card>
          </Col>;
        })}
      </Row>
    </>}

    <Modal title={editing ? `管理${groupTitle(editing.name)}` : '管理持仓'} open={!!editing}
      onOk={() => void save()} onCancel={() => setEditing(null)} confirmLoading={saving}
      cancelButtonProps={{ disabled: saving }} closable={!saving} mask={{ closable: !saving }}
      okText="保存持仓" cancelText="取消" width={760}>
      <Text type="secondary">填写标的、行业和占所属类别的比例。行业可留空，AI 会把缺失部分视为未知；基金行业标签不代表成分穿透。</Text>
      <Form form={form} layout="vertical" style={{ marginTop: 20 }} disabled={saving}>
        <Form.List name="items" rules={[{ validator: async (_, value) => { validateHoldings(value ?? []); } }]}>
          {(fields, { add, remove }, { errors }) => <>
            {fields.map(field => <Row key={field.key} gutter={8} align="top">
              <Col xs={24} sm={10}>
                <Form.Item name={[field.name, 'name']} label="标的名称" rules={[
                  { required: true, whitespace: true, message: '请输入标的名称' },
                  { max: 80, message: '名称最多80个字符' },
                ]}>
                  <Input maxLength={80} placeholder={editing ? examples(editing.name) : '标的名称'} />
                </Form.Item>
              </Col>
              <Col xs={14} sm={7}>
                <Form.Item name={[field.name, 'sector']} label="行业（可选）">
                  <Select allowClear showSearch={{ optionFilterProp: 'label' }} options={SECTOR_OPTIONS} placeholder="未标注" />
                </Form.Item>
              </Col>
              <Col xs={8} sm={5}>
                <Form.Item name={[field.name, 'percentage']} label="类内占比" rules={[
                  { required: true, message: '请输入占比' },
                  { type: 'number', min: 0.01, max: 100, message: '占比范围为0.01%至100%' },
                ]}>
                  <InputNumber min={0.01} max={100} step={1} precision={2} suffix="%" style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col xs={2} sm={2}>
                <Button danger type="text" icon={<DeleteOutlined />} aria-label={`删除第${field.name + 1}项持仓`}
                  style={{ marginTop: 30 }} onClick={() => remove(field.name)} />
              </Col>
            </Row>)}
            <Form.ErrorList errors={errors} />
            <Button type="dashed" block icon={<PlusOutlined />} disabled={saving || fields.length >= 200}
              style={{ marginTop: 12 }} onClick={() => add({ name: '', percentage: undefined })}>新增标的</Button>
          </>}
        </Form.List>
      </Form>
      <Alert style={{ marginTop: 16 }} type={total > 100 ? 'error' : 'info'} showIcon
        title={`合计 ${formatPercentage(total)} · ${total > 100 ? '已超出100%' : `未登记 ${formatPercentage(100 - total)}`}`}
        description={items?.length === 0 ? '保存后将清空该类别的具体持仓。' : '每个标的占比最多保留两位小数，同一类别的名称不能重复。'} />
    </Modal>
  </div>;
}
