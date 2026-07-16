/**
 * 趋势分析页
 *
 * 支持：
 * - 时间范围：7天 / 30天 / 90天 / 自定义
 * - 聚合：日 / 周 / 月
 * - 按分类动态生成折线图
 * - 数据不足时显示说明，不伪造数据
 */
import React, { useMemo, useState } from 'react';
import {
  Card,
  Select,
  Space,
  DatePicker,
  Segmented,
  Empty,
  Typography,
  Spin,
  message,
} from 'antd';
import ReactECharts from 'echarts-for-react';
import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';
import PageHeader from '../../components/PageHeader';
import EmptyState from '../../components/EmptyState';
import { useSnapshots, useCategories } from '../../app/storage';
import { getCategoryTrendData, aggregateTrendData, type Aggregation } from '../../utils/snapshot';
import { formatAmount } from '../../utils/amount';
import { isValidDateRange } from '../../utils/date';

dayjs.extend(customParseFormat);

const RANGE_PRESETS: { label: string; days: number }[] = [
  { label: '近7天', days: 7 },
  { label: '近30天', days: 30 },
  { label: '近90天', days: 90 },
];

const COLORS = [
  '#1890ff', '#52c41a', '#faad14', '#f5222d', '#722ed1',
  '#13c2c2', '#eb2f96', '#fa8c16', '#2f54eb', '#a0d911',
];

const Trends: React.FC = () => {
  const { snapshots, loading } = useSnapshots();
  const { categories } = useCategories();

  const [rangePreset, setRangePreset] = useState<number>(30);
  const [customRange, setCustomRange] = useState<[string, string] | null>(null);
  const [aggregation, setAggregation] = useState<Aggregation>('day');
  const [selectedCategoryIds, setSelectedCategoryIds] = useState<string[]>([]);

  // 确定实际起止日期
  const dateRange = useMemo((): [string, string] => {
    if (customRange) return customRange;
    const end = dayjs().format('YYYY-MM-DD');
    const start = dayjs().subtract(rangePreset - 1, 'day').format('YYYY-MM-DD');
    return [start, end];
  }, [rangePreset, customRange]);

  // 过滤时间范围内的快照
  const rangeSnapshots = useMemo(() => {
    return snapshots
      .filter((s) => s.snapshotDate >= dateRange[0] && s.snapshotDate <= dateRange[1])
      .sort((a, b) => a.snapshotDate.localeCompare(b.snapshotDate));
  }, [snapshots, dateRange]);

  // 默认选中所有分类
  const displayCategoryIds = useMemo(() => {
    if (selectedCategoryIds.length > 0) return selectedCategoryIds;
    return categories.map((c) => c.id);
  }, [selectedCategoryIds, categories]);

  // 按分类生成趋势数据（仅使用范围内的快照）
  const categorySeries = useMemo(() => {
    if (aggregation === 'day') {
      return displayCategoryIds.map((catId, idx) => {
        const cat = categories.find((c) => c.id === catId);
        const rawData = getCategoryTrendData(snapshots, catId, dateRange[0], dateRange[1]);
        return {
          name: cat?.name ?? catId,
          color: COLORS[idx % COLORS.length],
          data: rawData.map((d) => ({
            date: d.date,
            value: parseFloat(d.amount),
          })),
        };
      });
    }
    // 对于周/月聚合，需要手动聚合每个分类的数据
    return displayCategoryIds.map((catId, idx) => {
      const cat = categories.find((c) => c.id === catId);
      const rawData = getCategoryTrendData(snapshots, catId, dateRange[0], dateRange[1]);
      const aggregated = aggregateTrendData(
        rawData.map((d) => ({ date: d.date, totalAmount: d.amount })),
        aggregation
      );
      return {
        name: cat?.name ?? catId,
        color: COLORS[idx % COLORS.length],
        data: aggregated.map((d) => ({
          date: d.label,
          value: parseFloat(d.totalAmount),
        })),
      };
    });
  }, [snapshots, displayCategoryIds, aggregation, dateRange, categories]);

  // 构建 ECharts option
  const chartOption = useMemo(() => {
    if (categorySeries.length === 0) return {};
    // x 轴标签：取第一个系列的日期
    const xLabels = categorySeries[0]?.data.map((d) => d.date) ?? [];

    return {
      tooltip: {
        trigger: 'axis' as const,
        formatter: (params: { seriesName: string; name: string; value: number }[]) => {
          const lines = params.map((p) => `${p.seriesName}: ¥${formatAmount(p.value)}`);
          return `${params[0]?.name ?? ''}<br/>${lines.join('<br/>')}`;
        },
      },
      legend: {
        type: 'scroll' as const,
        bottom: 0,
        data: categorySeries.map((s) => s.name),
      },
      grid: { left: 60, right: 20, top: 20, bottom: 50 },
      xAxis: {
        type: 'category' as const,
        data: xLabels,
        axisLabel: { rotate: 45, fontSize: 11 },
      },
      yAxis: {
        type: 'value' as const,
        axisLabel: { formatter: (v: number) => `¥${(v / 10000).toFixed(0)}万` },
      },
      series: categorySeries.map((s) => ({
        name: s.name,
        type: 'line',
        data: s.data.map((d) => d.value),
        smooth: true,
        symbol: 'circle',
        symbolSize: 4,
        lineStyle: { color: s.color, width: 2 },
        itemStyle: { color: s.color },
      })),
    };
  }, [categorySeries]);

  // 数据不足说明
  const dataInsufficient = rangeSnapshots.length < 2;

  if (loading) {
    return <Spin size="large" style={{ display: 'block', marginTop: 120 }} />;
  }

  if (snapshots.length === 0) {
    return (
      <>
        <PageHeader title="趋势分析" subtitle="查看资产变化趋势与分类走势" />
        <EmptyState description="还没有快照数据，请先添加快照" />
      </>
    );
  }

  return (
    <>
      <PageHeader title="趋势分析" subtitle="查看资产变化趋势与分类走势" />

      {/* 控制栏 */}
      <Space wrap style={{ marginBottom: 16 }}>
        <Segmented
          options={RANGE_PRESETS.map((r) => ({ label: r.label, value: r.days }))}
          value={rangePreset}
          onChange={(v) => {
            setRangePreset(v as number);
            setCustomRange(null);
          }}
        />
        <DatePicker.RangePicker
          placeholder={['开始日期', '结束日期']}
          value={
            customRange
              ? [dayjs(customRange[0], 'YYYY-MM-DD', true), dayjs(customRange[1], 'YYYY-MM-DD', true)]
              : undefined
          }
          onChange={(dates) => {
            if (dates && dates[0] && dates[1]) {
              const start = dates[0].format('YYYY-MM-DD');
              const end = dates[1].format('YYYY-MM-DD');
              const range = isValidDateRange(start, end);
              if (!range.valid) {
                message.warning(range.error ?? '日期范围无效');
                return;
              }
              setCustomRange([start, end]);
              setRangePreset(0);
            } else {
              setCustomRange(null);
              setRangePreset(30);
            }
          }}
        />
        <Segmented
          options={[
            { label: '按日', value: 'day' },
            { label: '按周', value: 'week' },
            { label: '按月', value: 'month' },
          ]}
          value={aggregation}
          onChange={(v) => setAggregation(v as Aggregation)}
        />
        <Select
          mode="multiple"
          placeholder="选择分类（默认全部）"
          style={{ minWidth: 240 }}
          value={selectedCategoryIds}
          onChange={setSelectedCategoryIds}
          options={categories.map((c) => ({ label: c.name, value: c.id }))}
          allowClear
          maxTagCount={3}
        />
      </Space>

      {/* 数据不足提示 */}
      {dataInsufficient && (
        <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
          当前时间范围内快照数量较少（{rangeSnapshots.length} 条），趋势仅供参考。建议添加更多日期的快照数据。
        </Typography.Text>
      )}

      {/* 趋势图 */}
      <Card title="分类资产趋势">
        {categorySeries.length > 0 ? (
          <ReactECharts option={chartOption} style={{ height: 420 }} />
        ) : (
          <Empty description="所选分类无数据" />
        )}
      </Card>
    </>
  );
};

export default Trends;
