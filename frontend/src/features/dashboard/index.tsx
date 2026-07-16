import React, { useMemo, useState } from 'react';
import { Card, Col, Row, Statistic, Table, Segmented, Spin } from 'antd';
import {
  WalletOutlined,
  AppstoreOutlined,
  CalendarOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import PageHeader from '../../components/PageHeader';
import AmountText from '../../components/AmountText';
import EmptyState from '../../components/EmptyState';
import { useCategories } from '../../app/storage';
import { useSnapshots } from '../../app/storage';
import {
  getCategoryPercentages,
  getTrendData,
  aggregateTrendData,
  type Aggregation,
} from '../../utils/snapshot';
import { formatAmount } from '../../utils/amount';

const TREND_RANGES: { label: string; days: number }[] = [
  { label: '近7天', days: 7 },
  { label: '近30天', days: 30 },
  { label: '近90天', days: 90 },
];

const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const { categories } = useCategories();
  const { snapshots, loading } = useSnapshots();
  const [trendDays, setTrendDays] = useState(30);
  const [aggregation, setAggregation] = useState<Aggregation>('day');

  const sortedSnapshots = useMemo(
    () => [...snapshots].sort((a, b) => b.snapshotDate.localeCompare(a.snapshotDate)),
    [snapshots]
  );

  const latestSnapshot = sortedSnapshots[0];
  const categoryPieData = useMemo(() => getCategoryPercentages(latestSnapshot?.items ?? []), [latestSnapshot]);

  const trendRaw = useMemo(() => {
    if (snapshots.length === 0) return [];
    const end = latestSnapshot.snapshotDate;
    const start = dayjs(end).subtract(trendDays - 1, 'day').format('YYYY-MM-DD');
    return getTrendData(snapshots, start, end);
  }, [snapshots, trendDays, latestSnapshot]);

  const trendData = useMemo(
    () => aggregateTrendData(trendRaw, aggregation),
    [trendRaw, aggregation]
  );

  // --- pie chart option ---
  const pieOption = useMemo(() => {
    if (categoryPieData.length === 0) return {};
    return {
      tooltip: {
        trigger: 'item' as const,
        formatter: (params: { name: string; value: number; percent: number }) =>
          `${params.name}: ¥${formatAmount(params.value)} (${params.percent}%)`,
      },
      legend: { bottom: 0, type: 'scroll' as const },
      series: [
        {
          type: 'pie',
          radius: ['45%', '70%'],
          center: ['50%', '45%'],
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          emphasis: {
            label: { show: true, fontSize: 14, fontWeight: 'bold' },
          },
          data: categoryPieData.map((c) => ({
            name: c.categoryName,
            value: parseFloat(c.amount),
          })),
        },
      ],
    };
  }, [categoryPieData]);

  // --- trend line option ---
  const trendOption = useMemo(() => {
    if (trendData.length === 0) return {};
    return {
      tooltip: {
        trigger: 'axis' as const,
        formatter: (params: { name: string; value: number }[]) => {
          const p = params[0];
          return `${p.name}<br/>总资产: ¥${formatAmount(p.value)}`;
        },
      },
      grid: { left: 60, right: 20, top: 20, bottom: 30 },
      xAxis: {
        type: 'category' as const,
        data: trendData.map((d) => d.label),
        axisLabel: { rotate: 45, fontSize: 11 },
      },
      yAxis: {
        type: 'value' as const,
        axisLabel: { formatter: (v: number) => `¥${(v / 10000).toFixed(0)}万` },
      },
      series: [
        {
          type: 'line',
          data: trendData.map((d) => parseFloat(d.totalAmount)),
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(24,144,255,0.3)' },
                { offset: 1, color: 'rgba(24,144,255,0.02)' },
              ],
            },
          },
          lineStyle: { color: '#1890ff', width: 2 },
          itemStyle: { color: '#1890ff' },
        },
      ],
    };
  }, [trendData]);

  // --- category table columns ---
  const categoryColumns = [
    { title: '分类', dataIndex: 'categoryName', key: 'categoryName' },
    {
      title: '金额',
      dataIndex: 'amount',
      key: 'amount',
      render: (v: string) => <AmountText amount={v} />,
    },
    {
      title: '占比',
      dataIndex: 'percent',
      key: 'percent',
      render: (v: number) => `${v}%`,
    },
  ];

  if (loading) {
    return <Spin size="large" style={{ display: 'block', marginTop: 120 }} />;
  }

  if (snapshots.length === 0) {
    return (
      <>
        <PageHeader title="资产总览" subtitle="查看您的资产分布与趋势" />
        <EmptyState
          description="还没有资产快照，请先添加一条快照开始记录"
          actionLabel="新增第一条快照"
          onAction={() => navigate('/snapshots')}
        />
      </>
    );
  }

  return (
    <>
      <PageHeader title="资产总览" subtitle="查看您的资产分布与趋势" />

      {/* 统计卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="总资产"
              valueRender={() => (
                <AmountText
                  amount={latestSnapshot?.totalAmount ?? '0'}
                  style={{ fontSize: 24, fontWeight: 600, color: '#1890ff' }}
                />
              )}
              prefix={<WalletOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="分类数量"
              value={categories.length}
              prefix={<AppstoreOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="最近快照日期"
              value={latestSnapshot?.snapshotDate ?? '-'}
              prefix={<CalendarOutlined />}
            />
          </Card>
        </Col>
      </Row>

      {/* 环形图 + 分类明细 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} lg={12}>
          <Card title="资产分类占比">
            {categoryPieData.length > 0 ? (
              <ReactECharts option={pieOption} style={{ height: 360 }} />
            ) : (
              <EmptyState description="暂无分类数据" />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="分类金额明细">
            <Table
              dataSource={categoryPieData.map((c, i) => ({ ...c, key: i }))}
              columns={categoryColumns}
              pagination={false}
              size="small"
            />
          </Card>
        </Col>
      </Row>

      {/* 趋势图 */}
      <Card
        title="资产趋势"
        extra={
          <Segmented
            options={TREND_RANGES.map((r) => ({ label: r.label, value: r.days }))}
            value={trendDays}
            onChange={(v) => setTrendDays(v as number)}
          />
        }
      >
        <div style={{ marginBottom: 12 }}>
          <Segmented
            options={[
              { label: '按日', value: 'day' },
              { label: '按周', value: 'week' },
              { label: '按月', value: 'month' },
            ]}
            value={aggregation}
            onChange={(v) => setAggregation(v as Aggregation)}
          />
        </div>
        {trendData.length > 0 ? (
          <ReactECharts option={trendOption} style={{ height: 320 }} />
        ) : (
          <EmptyState description="所选时间范围内暂无快照数据" />
        )}
      </Card>
    </>
  );
};

export default Dashboard;
