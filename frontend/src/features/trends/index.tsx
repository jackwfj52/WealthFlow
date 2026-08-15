/**
 * 趋势分析页
 *
 * 支持：
 * - 时间范围：7天 / 30天 / 90天 / 自定义
 * - 聚合：日 / 周 / 月
 * - 总资产曲线（可开关）+ 按分类动态生成折线图
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
  theme,
  Checkbox,
} from 'antd';
import ReactECharts from 'echarts-for-react';
import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';
import PageHeader from '../../components/PageHeader';
import EmptyState from '../../components/EmptyState';
import { useSnapshots, useCategories } from '../../app/storage';
import { useSettings } from '../../app/settings';
import {
  getCategoryTrendData,
  getTrendData,
  aggregateTrendData,
  type Aggregation,
} from '../../utils/snapshot';
import { formatCurrency, pickAmountUnit, formatAxisAmount } from '../../utils/amount';
import { isValidDateRange } from '../../utils/date';
import { hexToRgba } from '../../utils/color';

dayjs.extend(customParseFormat);

const RANGE_PRESETS: { label: string; days: number }[] = [
  { label: '近7天', days: 7 },
  { label: '近30天', days: 30 },
  { label: '近90天', days: 90 },
  { label: '近一年', days: 365 },
  { label: '近十年', days: 3650 },
];

const COLORS = [
  '#1890ff', '#52c41a', '#faad14', '#f5222d', '#722ed1',
  '#13c2c2', '#eb2f96', '#fa8c16', '#2f54eb', '#a0d911',
];

// 总资产曲线专用色（火山橙），避开 COLORS 调色板，防止图例颜色与分类重复
const TOTAL_COLOR = '#fa541c';

const Trends: React.FC = () => {
  const { snapshots, loading } = useSnapshots();
  const { categories } = useCategories();
  const { settings } = useSettings();
  const { token } = theme.useToken();

  const [rangePreset, setRangePreset] = useState<number>(settings.defaultTrendDays);
  const [customRange, setCustomRange] = useState<[string, string] | null>(null);
  const [aggregation, setAggregation] = useState<Aggregation>(settings.defaultAggregation);
  const [selectedCategoryIds, setSelectedCategoryIds] = useState<string[]>([]);
  const [showTotal, setShowTotal] = useState(true);

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

  // 总资产趋势（不受分类筛选影响，始终展示）
  const totalSeries = useMemo(() => {
    const rawData = getTrendData(snapshots, dateRange[0], dateRange[1]);
    const aggregated = aggregateTrendData(rawData, aggregation);
    return {
      name: '总资产',
      data: aggregated.map((d) => ({
        date: d.label,
        value: parseFloat(d.totalAmount),
      })),
    };
  }, [snapshots, dateRange, aggregation]);

  // 构建 ECharts option
  const chartOption = useMemo(() => {
    const showTotalSeries = showTotal && totalSeries.data.length > 0;
    if (!showTotalSeries && categorySeries.length === 0) return {};
    // x 轴标签：总资产序列覆盖范围内所有快照日期，优先取它
    const xLabels = showTotalSeries
      ? totalSeries.data.map((d) => d.date)
      : (categorySeries[0]?.data.map((d) => d.date) ?? []);

    // 数据点较多时启用横向缩放，默认显示最近 60 个点
    const manyPoints = xLabels.length > 90;
    const VISIBLE_POINTS = 60;
    const zoomStart = manyPoints
      ? Math.max(0, (1 - VISIBLE_POINTS / xLabels.length) * 100)
      : 0;

    // y 轴单位按数据最大值自适应（百/千/万/十万...）
    let maxValue = 0;
    const allSeries = showTotalSeries ? [totalSeries, ...categorySeries] : categorySeries;
    for (const s of allSeries) {
      for (const d of s.data) {
        maxValue = Math.max(maxValue, d.value);
      }
    }
    const amountUnit = pickAmountUnit(maxValue);

    return {
      tooltip: {
        trigger: 'axis' as const,
        backgroundColor: token.colorBgElevated,
        borderColor: token.colorSplit,
        textStyle: { color: token.colorText },
        formatter: (params: { seriesName: string; name: string; value: number }[]) => {
          const lines = params.map(
            (p) =>
              `${p.seriesName}: ${formatCurrency(p.value, settings.currencySymbol, {
                thousands: settings.thousandsSeparator,
              })}`
          );
          return `${params[0]?.name ?? ''}<br/>${lines.join('<br/>')}`;
        },
      },
      legend: {
        type: 'scroll' as const,
        bottom: manyPoints ? 30 : 0,
        padding: [8, 0, 0, 0],
        data: showTotalSeries
          ? [totalSeries.name, ...categorySeries.map((s) => s.name)]
          : categorySeries.map((s) => s.name),
        textStyle: { color: token.colorText },
        pageIconColor: token.colorTextSecondary,
        pageTextStyle: { color: token.colorTextSecondary },
      },
      grid: { left: 60, right: 20, top: 20, bottom: manyPoints ? 100 : 72 },
      xAxis: {
        type: 'category' as const,
        data: xLabels,
        axisLabel: { rotate: 45, fontSize: 11, color: token.colorTextSecondary },
        axisLine: { lineStyle: { color: token.colorSplit } },
      },
      yAxis: {
        type: 'value' as const,
        axisLabel: {
          color: token.colorTextSecondary,
          formatter: (v: number) => formatAxisAmount(v, amountUnit, settings.currencySymbol),
        },
        axisLine: { lineStyle: { color: token.colorSplit } },
        splitLine: { lineStyle: { color: token.colorSplit } },
      },
      dataZoom: manyPoints
        ? [
            { type: 'inside' },
            {
              type: 'slider',
              height: 24,
              bottom: 0,
              start: zoomStart,
              end: 100,
              brushSelect: false,
              borderColor: token.colorSplit,
              backgroundColor: token.colorFillTertiary,
              fillerColor: hexToRgba(token.colorPrimary, 0.25),
              dataBackground: {
                lineStyle: { color: token.colorSplit },
                areaStyle: { color: token.colorFillTertiary },
              },
              selectedDataBackground: {
                lineStyle: { color: token.colorPrimary },
                areaStyle: { color: hexToRgba(token.colorPrimary, 0.25) },
              },
              handleStyle: {
                color: token.colorBgElevated,
                borderColor: token.colorBorderSecondary,
              },
              moveHandleStyle: { color: token.colorSplit },
              textStyle: { color: token.colorTextSecondary },
            },
          ]
        : [],
      series: [
        ...(showTotalSeries
          ? [
              {
                name: totalSeries.name,
                type: 'line',
                data: totalSeries.data.map((d) => d.value),
                smooth: true,
                symbol: 'circle',
                symbolSize: 6,
                areaStyle: {
                  color: {
                    type: 'linear',
                    x: 0, y: 0, x2: 0, y2: 1,
                    colorStops: [
                      { offset: 0, color: hexToRgba(TOTAL_COLOR, 0.3) },
                      { offset: 1, color: hexToRgba(TOTAL_COLOR, 0.02) },
                    ],
                  },
                },
                lineStyle: { color: TOTAL_COLOR, width: 3 },
                itemStyle: { color: TOTAL_COLOR },
              },
            ]
          : []),
        ...categorySeries.map((s) => ({
          name: s.name,
          type: 'line',
          data: s.data.map((d) => d.value),
          smooth: true,
          symbol: 'circle',
          symbolSize: 4,
          lineStyle: { color: s.color, width: 2 },
          itemStyle: { color: s.color },
        })),
      ],
    };
  }, [totalSeries, categorySeries, settings, token, showTotal]);

  // 数据不足说明
  const dataInsufficient = rangeSnapshots.length < 2;

  // 是否还有可展示的曲线
  const hasChartData =
    (showTotal && totalSeries.data.length > 0) || categorySeries.length > 0;

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
        <Checkbox
          checked={showTotal}
          onChange={(e) => setShowTotal(e.target.checked)}
        >
          显示总资产
        </Checkbox>
      </Space>

      {/* 数据不足提示 */}
      {dataInsufficient && (
        <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
          当前时间范围内快照数量较少（{rangeSnapshots.length} 条），趋势仅供参考。建议添加更多日期的快照数据。
        </Typography.Text>
      )}

      {/* 趋势图 */}
      <Card title="资产趋势">
        {hasChartData ? (
          <ReactECharts notMerge option={chartOption} style={{ height: 420 }} />
        ) : (
          <Empty description="当前时间范围内无快照数据" />
        )}
      </Card>
    </>
  );
};

export default Trends;
