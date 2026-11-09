<script lang="ts" setup>
import type {
  ModelCallDistributionItem,
  ModelCallMetricCard,
  ModelCallOverview,
  ModelCallRecentItem,
  ModelCallTrendPoint,
  ModelCallUsage,
  ModelCallUsageDistributionItem,
  ModelCallUsageItem,
  ModelCallUsageModelItem,
  ModelCallUsageTrendPoint,
} from '#/api/core/monitor';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import {
  Activity,
  Cpu,
  RefreshCcw,
  Server,
  TriangleAlert,
  Zap,
} from '@vben/icons';
import { $t } from '@vben/locales';
import { useEcharts, EchartsUI } from '@vben/plugins/echarts';
import { usePreferences } from '@vben/preferences';

import type { DataTableColumns } from 'naive-ui';

import {
  NButton,
  NDataTable,
  NEmpty,
  NPagination,
  NProgress,
  NRadioButton,
  NRadioGroup,
  NSpin,
  NTag,
} from 'naive-ui';

import {
  getModelCallOverviewApi,
  getModelCallUsageApi,
} from '#/api/core/monitor';
import { message } from '#/adapter/naive';
import LcCard from '#/components/LcCard/index.vue';
import LcEmptyState from '#/components/LcEmptyState/index.vue';

type MonitorView = 'calls' | 'usage';

const loading = ref(false);
const windowDays = ref(7);
const overview = ref<ModelCallOverview>({});
const viewMode = ref<MonitorView>('calls');

const usageLoading = ref(false);
const usage = ref<ModelCallUsage>({});
const usagePageNo = ref(1);
const usagePageSize = ref(10);

const { isDark } = usePreferences();

const trendChartRef = ref();
const callTypeChartRef = ref();
const providerChartRef = ref();
const usageTrendChartRef = ref();
const usageSceneChartRef = ref();
const usageCallTypeChartRef = ref();
const { renderEcharts: renderTrendChart } = useEcharts(trendChartRef);
const { renderEcharts: renderCallTypeChart } = useEcharts(callTypeChartRef);
const { renderEcharts: renderProviderChart } = useEcharts(providerChartRef);
const { renderEcharts: renderUsageTrendChart } = useEcharts(usageTrendChartRef);
const { renderEcharts: renderUsageSceneChart } = useEcharts(usageSceneChartRef);
const { renderEcharts: renderUsageCallTypeChart } =
  useEcharts(usageCallTypeChartRef);

/** 图表配色跟随明暗主题，保持与设计令牌一致的语义色。 */
const chartPalette = computed(() => {
  if (isDark.value) {
    return {
      axis: '#94a3b8',
      axisLine: '#334155',
      split: 'rgba(148, 163, 184, 0.16)',
      legend: '#94a3b8',
      text: '#cbd5e1',
      seriesPrimary: '#60a5fa',
      seriesSecondary: '#38bdf8',
      areaOpacity: 0.18,
      pieBorder: 'hsl(220 14% 13%)',
      tooltipBg: 'hsl(220 12% 15%)',
      tooltipBorder: '#334155',
      tooltipText: '#e2e8f0',
    };
  }
  return {
    axis: '#64748b',
    axisLine: '#cbd5e1',
    split: 'rgba(148, 163, 184, 0.22)',
    legend: '#64748b',
    text: '#475569',
    seriesPrimary: '#2563eb',
    seriesSecondary: '#0ea5e9',
    areaOpacity: 0.14,
    pieBorder: '#fff',
    tooltipBg: '#fff',
    tooltipBorder: 'hsl(214 18% 91%)',
    tooltipText: '#334155',
  };
});

/** 统计卡片文案来自后端埋点，这里按已知枚举值映射到本地化键，未命中时原样展示。 */
const METRIC_LABEL_KEYS: Record<string, string> = {
  调用总量: 'monitor.metrics.totalCalls',
  'Token 消耗': 'monitor.metrics.tokens',
  平均耗时: 'monitor.metrics.avgDuration',
  调用成功率: 'monitor.metrics.successRate',
};

/** 统计卡片 hint 来自后端埋点，按已知枚举值映射到本地化键，未命中时原样展示。 */
const METRIC_HINT_KEYS: Record<string, string> = {
  统计窗口内的模型调用次数: 'monitor.hints.totalCalls',
  '输入与输出 Token 合计': 'monitor.hints.tokens',
  单次模型调用的平均响应时间: 'monitor.hints.avgDuration',
  当前窗口无失败调用: 'monitor.hints.successRate',
};

function metricHint(hint?: string) {
  const key = METRIC_HINT_KEYS[String(hint ?? '')];
  return key ? $t(key) : String(hint ?? '');
}

const CALL_TYPE_LABEL_KEYS: Record<string, string> = {
  CHAT: 'monitor.callType.CHAT',
  EMBEDDING: 'monitor.callType.EMBEDDING',
  IMAGE: 'monitor.callType.IMAGE',
  OCR: 'monitor.callType.OCR',
};

const SCENE_LABEL_KEYS: Record<string, string> = {
  AGENT_CHAT: 'monitor.scene.AGENT_CHAT',
  DATA_ANALYSIS: 'monitor.scene.DATA_ANALYSIS',
  KNOWLEDGE_INDEX: 'monitor.scene.KNOWLEDGE_INDEX',
  KNOWLEDGE_RETRIEVE: 'monitor.scene.KNOWLEDGE_RETRIEVE',
  IMAGE_GENERATE: 'monitor.scene.IMAGE_GENERATE',
  IMAGE_OCR: 'monitor.scene.IMAGE_OCR',
  unknown: 'monitor.scene.unknown',
};

const windowOptions = computed(() =>
  [7, 14, 30].map((days) => ({
    label: $t('monitor.window.days', { days }),
    value: days,
  })),
);

const metrics = computed<ModelCallMetricCard[]>(
  () => overview.value.metrics ?? [],
);
const trend = computed<ModelCallTrendPoint[]>(() => overview.value.trend ?? []);
const callTypeDistribution = computed<ModelCallDistributionItem[]>(
  () => overview.value.callTypeDistribution ?? [],
);
const providerDistribution = computed<ModelCallDistributionItem[]>(
  () => overview.value.providerDistribution ?? [],
);
const topModels = computed<ModelCallUsageItem[]>(
  () => overview.value.topModels ?? [],
);
const recentCalls = computed<ModelCallRecentItem[]>(
  () => overview.value.recentCalls ?? [],
);

// ---------- 用量分析 ----------
const viewOptions = computed(() => [
  { label: $t('monitor.view.calls'), value: 'calls' as const },
  { label: $t('monitor.view.usage'), value: 'usage' as const },
]);

const quota = computed(() => usage.value.quota ?? {});
const usageTotals = computed(() => usage.value.totals ?? {});
const usageTrend = computed<ModelCallUsageTrendPoint[]>(
  () => usage.value.trend ?? [],
);
const usageByModel = computed<ModelCallUsageModelItem[]>(
  () => usage.value.byModel ?? [],
);
const usageByScene = computed<ModelCallUsageDistributionItem[]>(
  () => usage.value.byScene ?? [],
);
const usageByCallType = computed<ModelCallUsageDistributionItem[]>(
  () => usage.value.byCallType ?? [],
);
const usageRecentCalls = computed<ModelCallRecentItem[]>(
  () => usage.value.recentCalls?.records ?? [],
);
const usageRecentTotal = computed(() => usage.value.recentCalls?.total ?? 0);

/** 配额使用百分比，limit=0（不限额）时为 0。 */
const quotaLimit = computed(() => Number(quota.value.dailyTokenLimit ?? 0));
const quotaPercent = computed(() => {
  if (quotaLimit.value <= 0) {
    return 0;
  }
  const percent = Number(quota.value.usagePercent ?? 0);
  return Math.min(100, Math.max(0, Number.isFinite(percent) ? percent : 0));
});
const quotaPercentText = computed(() => quotaPercent.value.toFixed(1));

const usageMetricCards = computed(() => [
  {
    hint: $t('monitor.usage.totals.hintCalls'),
    icon: Activity,
    key: 'calls',
    label: $t('monitor.usage.totals.calls'),
    value: formatNumber(usageTotals.value.calls),
  },
  {
    hint: $t('monitor.usage.totals.hintTokens'),
    icon: Zap,
    key: 'tokens',
    label: $t('monitor.usage.totals.tokens'),
    value: formatNumber(usageTotals.value.tokens),
  },
  {
    hint: $t('monitor.usage.totals.hintSuccessRate'),
    icon: TriangleAlert,
    key: 'successRate',
    label: $t('monitor.usage.totals.successRate'),
    value: `${Number(usageTotals.value.successRate ?? 0).toFixed(2)}%`,
  },
  {
    hint: $t('monitor.usage.totals.hintAvgDuration'),
    icon: Server,
    key: 'avgDuration',
    label: $t('monitor.usage.totals.avgDuration'),
    value: formatDuration(usageTotals.value.avgDuration),
  },
]);

const usageModelColumns = computed<
  DataTableColumns<ModelCallUsageModelItem>
>(() => [
  {
    key: 'modelName',
    title: $t('monitor.usage.byModel.columns.model'),
    width: 170,
    ellipsis: { tooltip: true },
    render: (row: ModelCallUsageModelItem) => row.modelName || '--',
  },
  {
    key: 'provider',
    title: $t('monitor.usage.byModel.columns.provider'),
    width: 120,
    render: (row: ModelCallUsageModelItem) => row.provider || '--',
  },
  {
    key: 'calls',
    title: $t('monitor.usage.byModel.columns.calls'),
    width: 100,
    align: 'right',
    render: (row: ModelCallUsageModelItem) => formatNumber(row.calls),
  },
  {
    key: 'tokens',
    title: $t('monitor.usage.byModel.columns.tokens'),
    width: 120,
    align: 'right',
    render: (row: ModelCallUsageModelItem) => formatNumber(row.tokens),
  },
  {
    key: 'errorCount',
    title: $t('monitor.usage.byModel.columns.errorCount'),
    width: 100,
    align: 'right',
    render: (row: ModelCallUsageModelItem) => formatNumber(row.errorCount),
  },
  {
    key: 'avgDuration',
    title: $t('monitor.usage.byModel.columns.avgDuration'),
    width: 110,
    align: 'right',
    render: (row: ModelCallUsageModelItem) => formatDuration(row.avgDuration),
  },
]);

const usageRecentRows = computed(() =>
  usageRecentCalls.value.map((item) => ({
    ...item,
    callType: callTypeLabel(item.callType),
    scene: sceneLabel(item.scene),
    totalToken: formatNumber(item.totalToken),
    duration: formatDuration(item.duration),
    createTime: formatTime(item.createTime),
  })),
);

/** 模型消耗排行的最大值，用于计算进度条比例。 */
const maxModelToken = computed(() =>
  Math.max(1, ...topModels.value.map((item) => Number(item.tokenCount ?? 0))),
);

const recentColumns = computed(() => [
  {
    key: 'modelName',
    title: $t('monitor.columns.model'),
    width: 160,
    ellipsis: { tooltip: true },
  },
  { key: 'provider', title: $t('monitor.columns.provider'), width: 110 },
  { key: 'callType', title: $t('monitor.columns.type'), width: 100 },
  { key: 'scene', title: $t('monitor.columns.scene'), width: 130 },
  { key: 'totalToken', title: $t('monitor.chart.tokensLegend'), width: 90 },
  { key: 'duration', title: $t('monitor.columns.duration'), width: 90 },
  { key: 'status', title: $t('monitor.columns.status'), width: 90 },
  { key: 'createTime', title: $t('monitor.columns.createTime'), width: 160 },
]);

function resolveMetricIcon(label?: string) {
  const key = METRIC_LABEL_KEYS[String(label ?? '')];
  switch (key) {
    case 'monitor.metrics.avgDuration': {
      return Activity;
    }
    case 'monitor.metrics.successRate': {
      return TriangleAlert;
    }
    case 'monitor.metrics.tokens': {
      return Zap;
    }
    default: {
      return Cpu;
    }
  }
}

function metricLabel(label?: string) {
  const key = METRIC_LABEL_KEYS[String(label ?? '')];
  return key ? $t(key) : String(label ?? '');
}

function resolveToneType(tone?: string) {
  switch (tone) {
    case 'error': {
      return 'error';
    }
    case 'success': {
      return 'success';
    }
    case 'warning': {
      return 'warning';
    }
    default: {
      return 'primary';
    }
  }
}

function callTypeLabel(value?: string) {
  const key = CALL_TYPE_LABEL_KEYS[String(value ?? '')];
  return key ? $t(key) : String(value ?? '--');
}

function sceneLabel(value?: string) {
  const key = SCENE_LABEL_KEYS[String(value ?? '')];
  return key ? $t(key) : String(value ?? '--');
}

function formatNumber(value?: null | number) {
  if (value === null || value === undefined) {
    return '0';
  }
  return Number(value).toLocaleString('zh-CN');
}

function formatDuration(value?: null | number) {
  return value === null || value === undefined ? '--' : `${value} ms`;
}

function formatTime(timestamp?: number) {
  if (!timestamp) {
    return '--';
  }
  return new Date(timestamp).toLocaleString('zh-CN', { hour12: false });
}

/** 表格数据在渲染前做一次展示态转换，避免在模板里写判断逻辑。 */
const recentRows = computed(() =>
  recentCalls.value.map((item) => ({
    ...item,
    callType: callTypeLabel(item.callType),
    scene: sceneLabel(item.scene),
    totalToken: formatNumber(item.totalToken),
    duration: formatDuration(item.duration),
    createTime: formatTime(item.createTime),
  })),
);

function buildTrendOption(points: ModelCallTrendPoint[]) {
  const palette = chartPalette.value;
  const dates = points.map((item) => item.date || '--');
  const callsLegend = $t('monitor.chart.callsLegend');
  const tokensLegend = $t('monitor.chart.tokensLegend');
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: palette.tooltipBg,
      borderColor: palette.tooltipBorder,
      textStyle: { color: palette.tooltipText, fontSize: 11 },
    },
    legend: {
      data: [callsLegend, tokensLegend],
      top: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 16,
      textStyle: { color: palette.legend, fontSize: 11 },
    },
    grid: { top: 40, right: 24, bottom: 28, left: 52 },
    xAxis: {
      type: 'category',
      data: dates,
      axisTick: { show: false },
      axisLabel: { color: palette.axis, fontSize: 11 },
      axisLine: { lineStyle: { color: palette.axisLine } },
    },
    yAxis: [
      {
        type: 'value',
        name: $t('monitor.chart.countSeries'),
        nameTextStyle: { color: palette.axis, fontSize: 10 },
        axisLabel: { color: palette.axis, fontSize: 11 },
        splitLine: { lineStyle: { color: palette.split } },
      },
      {
        type: 'value',
        name: tokensLegend,
        nameTextStyle: { color: palette.axis, fontSize: 10 },
        axisLabel: { color: palette.axis, fontSize: 11 },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: callsLegend,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: points.map((item) => item.callCount ?? 0),
        lineStyle: { color: palette.seriesPrimary, width: 2.5 },
        itemStyle: { color: palette.seriesPrimary },
        areaStyle: { opacity: palette.areaOpacity },
      },
      {
        name: tokensLegend,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        yAxisIndex: 1,
        data: points.map((item) => item.tokenCount ?? 0),
        lineStyle: { color: palette.seriesSecondary, width: 2.5 },
        itemStyle: { color: palette.seriesSecondary },
      },
    ],
  };
}

function buildDistributionOption(items: ModelCallDistributionItem[]) {
  const palette = chartPalette.value;
  if (items.length === 0) {
    return {
      title: {
        text: $t('monitor.chart.empty'),
        left: 'center',
        top: 'center',
        textStyle: {
          color: palette.axis,
          fontSize: 12,
          fontWeight: 'normal',
        },
      },
      series: [],
    };
  }
  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: palette.tooltipBg,
      borderColor: palette.tooltipBorder,
      textStyle: { color: palette.tooltipText, fontSize: 11 },
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 14,
      textStyle: { color: palette.legend, fontSize: 11 },
    },
    series: [
      {
        type: 'pie',
        radius: ['44%', '68%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: palette.pieBorder, borderWidth: 2 },
        label: { color: palette.text, fontSize: 11 },
        data: items.map((item) => ({
          // 调用类型走中文映射，供应商名称直接展示
          name: resolveDistributionName(item),
          value: item.callCount ?? 0,
        })),
      },
    ],
  };
}

function resolveDistributionName(item: ModelCallDistributionItem) {
  const raw = String(item.name ?? '');
  const key = CALL_TYPE_LABEL_KEYS[raw];
  return key ? $t(key) : raw || $t('monitor.chart.unknown');
}

async function renderCharts() {
  await Promise.all([
    renderTrendChart(buildTrendOption(trend.value) as never),
    renderCallTypeChart(
      buildDistributionOption(callTypeDistribution.value) as never,
    ),
    renderProviderChart(
      buildDistributionOption(providerDistribution.value) as never,
    ),
  ]);
}

function buildUsageTrendOption(points: ModelCallUsageTrendPoint[]) {
  const palette = chartPalette.value;
  const dates = points.map((item) => item.date || '--');
  const callsLegend = $t('monitor.usage.totals.calls');
  const tokensLegend = $t('monitor.usage.totals.tokens');
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: palette.tooltipBg,
      borderColor: palette.tooltipBorder,
      textStyle: { color: palette.tooltipText, fontSize: 11 },
    },
    legend: {
      data: [callsLegend, tokensLegend],
      top: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 16,
      textStyle: { color: palette.legend, fontSize: 11 },
    },
    grid: { top: 40, right: 24, bottom: 28, left: 52 },
    xAxis: {
      type: 'category',
      data: dates,
      axisTick: { show: false },
      axisLabel: { color: palette.axis, fontSize: 11 },
      axisLine: { lineStyle: { color: palette.axisLine } },
    },
    yAxis: [
      {
        type: 'value',
        name: $t('monitor.chart.countSeries'),
        nameTextStyle: { color: palette.axis, fontSize: 10 },
        axisLabel: { color: palette.axis, fontSize: 11 },
        splitLine: { lineStyle: { color: palette.split } },
      },
      {
        type: 'value',
        name: tokensLegend,
        nameTextStyle: { color: palette.axis, fontSize: 10 },
        axisLabel: { color: palette.axis, fontSize: 11 },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: callsLegend,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: points.map((item) => item.calls ?? 0),
        lineStyle: { color: palette.seriesPrimary, width: 2.5 },
        itemStyle: { color: palette.seriesPrimary },
        areaStyle: { opacity: palette.areaOpacity },
      },
      {
        name: tokensLegend,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        yAxisIndex: 1,
        data: points.map((item) => item.tokens ?? 0),
        lineStyle: { color: palette.seriesSecondary, width: 2.5 },
        itemStyle: { color: palette.seriesSecondary },
      },
    ],
  };
}

/** 用量分布图：既可能是场景（中文映射），也可能是调用类型（枚举映射）。 */
function buildUsageDistributionOption(
  items: ModelCallUsageDistributionItem[],
  resolveName: (raw: string) => string,
) {
  const palette = chartPalette.value;
  if (items.length === 0) {
    return {
      title: {
        text: $t('monitor.chart.empty'),
        left: 'center',
        top: 'center',
        textStyle: {
          color: palette.axis,
          fontSize: 12,
          fontWeight: 'normal',
        },
      },
      series: [],
    };
  }
  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: palette.tooltipBg,
      borderColor: palette.tooltipBorder,
      textStyle: { color: palette.tooltipText, fontSize: 11 },
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 14,
      textStyle: { color: palette.legend, fontSize: 11 },
    },
    series: [
      {
        type: 'pie',
        radius: ['44%', '68%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: palette.pieBorder, borderWidth: 2 },
        label: { color: palette.text, fontSize: 11 },
        data: items.map((item) => ({
          name: resolveName(String(item.name ?? '')),
          value: item.calls ?? 0,
        })),
      },
    ],
  };
}

function resolveSceneName(raw: string) {
  const key = SCENE_LABEL_KEYS[raw];
  return key ? $t(key) : raw || $t('monitor.chart.unknown');
}

function resolveCallTypeName(raw: string) {
  const key = CALL_TYPE_LABEL_KEYS[raw];
  return key ? $t(key) : raw || $t('monitor.chart.unknown');
}

async function renderUsageCharts() {
  await Promise.all([
    renderUsageTrendChart(buildUsageTrendOption(usageTrend.value) as never),
    renderUsageSceneChart(
      buildUsageDistributionOption(usageByScene.value, resolveSceneName) as never,
    ),
    renderUsageCallTypeChart(
      buildUsageDistributionOption(
        usageByCallType.value,
        resolveCallTypeName,
      ) as never,
    ),
  ]);
}

async function loadUsage() {
  usageLoading.value = true;
  try {
    usage.value = await getModelCallUsageApi({
      pageNo: usagePageNo.value,
      pageSize: usagePageSize.value,
      windowDays: windowDays.value,
    });
    await nextTick();
    await renderUsageCharts();
  } catch {
    message.error($t('monitor.usage.loadFailed'));
  } finally {
    usageLoading.value = false;
  }
}

async function handleViewChange(value: MonitorView) {
  viewMode.value = value;
  if (value === 'usage') {
    await nextTick();
    await loadUsage();
  }
}

function handleUsagePageChange(page: number) {
  usagePageNo.value = page;
  void loadUsage();
}

function handleUsagePageSizeChange(size: number) {
  usagePageSize.value = size;
  usagePageNo.value = 1;
  void loadUsage();
}

function handleRefresh() {
  if (viewMode.value === 'usage') {
    void loadUsage();
  } else {
    void loadOverview();
  }
}

async function loadOverview() {
  loading.value = true;
  try {
    overview.value = await getModelCallOverviewApi(windowDays.value);
    await renderCharts();
  } finally {
    loading.value = false;
  }
}

async function handleWindowChange(value: number) {
  windowDays.value = value;
  if (viewMode.value === 'usage') {
    usagePageNo.value = 1;
    await loadUsage();
  } else {
    await loadOverview();
  }
}

// 明暗主题切换时重绘图表，保证坐标轴/图例颜色同步
watch(isDark, () => {
  if (viewMode.value === 'usage') {
    void renderUsageCharts();
  } else {
    void renderCharts();
  }
});

onMounted(loadOverview);
</script>

<template>
  <Page
    content-class="p-4"
    :description="$t('monitor.description')"
    header-class="px-4 py-3.5"
    :title="$t('monitor.title')"
  >
    <template #extra>
      <div class="flex flex-wrap items-center gap-2">
        <NRadioGroup
          :value="viewMode"
          size="small"
          @update:value="handleViewChange"
        >
          <NRadioButton
            v-for="item in viewOptions"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </NRadioButton>
        </NRadioGroup>
        <NRadioGroup
          :value="windowDays"
          size="small"
          @update:value="handleWindowChange"
        >
          <NRadioButton
            v-for="item in windowOptions"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </NRadioButton>
        </NRadioGroup>
        <NButton
          :loading="viewMode === 'usage' ? usageLoading : loading"
          secondary
          @click="handleRefresh"
        >
          <template #icon>
            <RefreshCcw class="size-3.5" />
          </template>
          {{ $t('common.actions.refresh') }}
        </NButton>
      </div>
    </template>

    <NSpin v-if="viewMode === 'calls'" :show="loading">
      <!-- 指标卡片 -->
      <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <LcCard
          v-for="item in metrics"
          :key="item.label"
          :hoverable="false"
          :icon-component="resolveMetricIcon(item.label)"
        >
          <template #header>
            <div class="min-w-0">
              <div class="truncate text-[13px] font-semibold text-foreground">
                {{ metricLabel(item.label) }}
              </div>
              <div class="mt-0.5 text-[10px] leading-4 text-muted-foreground">
                {{ metricHint(item.hint) }}
              </div>
            </div>
          </template>

          <div class="flex items-end justify-between gap-3">
            <div
              class="c-metric-value text-[26px] font-semibold leading-none tracking-tight text-foreground"
            >
              {{ item.value }}
            </div>
            <NTag
              :bordered="false"
              :type="resolveToneType(item.tone)"
              class="shrink-0"
              round
              size="small"
            >
              {{ $t('monitor.window.days', { days: windowDays }) }}
            </NTag>
          </div>
        </LcCard>
      </div>

      <!-- 趋势图 -->
      <div
        class="mt-3 rounded-lg border border-border bg-card p-4 shadow-[var(--shadow-card)]"
      >
        <div class="mb-3 flex items-start justify-between gap-3">
          <div>
            <div class="text-sm font-semibold text-foreground">
              {{ $t('monitor.trend.title') }}
            </div>
            <div class="mt-0.5 text-xs text-muted-foreground">
              {{ $t('monitor.trend.description') }}
            </div>
          </div>
          <span
            class="hidden shrink-0 items-center gap-1 rounded-md border border-border bg-muted/30 px-2 py-1 text-[10px] text-muted-foreground sm:inline-flex"
          >
            <Activity class="size-3" />
            {{ $t('monitor.chart.countSeries') }} /
            {{ $t('monitor.chart.tokensLegend') }}
          </span>
        </div>
        <EchartsUI ref="trendChartRef" height="300px" />
      </div>

      <!-- 分布图 -->
      <div class="mt-3 grid gap-3 lg:grid-cols-2">
        <div
          class="rounded-lg border border-border bg-card p-4 shadow-[var(--shadow-card)]"
        >
          <div class="mb-1">
            <div class="text-sm font-semibold text-foreground">
              {{ $t('monitor.typeDistribution.title') }}
            </div>
            <div class="mt-0.5 text-xs text-muted-foreground">
              {{ $t('monitor.typeDistribution.description') }}
            </div>
          </div>
          <EchartsUI ref="callTypeChartRef" height="260px" />
        </div>

        <div
          class="rounded-lg border border-border bg-card p-4 shadow-[var(--shadow-card)]"
        >
          <div class="mb-1">
            <div class="text-sm font-semibold text-foreground">
              {{ $t('monitor.providerDistribution.title') }}
            </div>
            <div class="mt-0.5 text-xs text-muted-foreground">
              {{ $t('monitor.providerDistribution.description') }}
            </div>
          </div>
          <EchartsUI ref="providerChartRef" height="260px" />
        </div>
      </div>

      <!-- 模型消耗排行 -->
      <div
        class="mt-3 rounded-lg border border-border bg-card p-4 shadow-[var(--shadow-card)]"
      >
        <div class="mb-3 flex items-center justify-between gap-3">
          <div>
            <div class="text-sm font-semibold text-foreground">
              {{ $t('monitor.ranking.title') }}
            </div>
            <div class="mt-0.5 text-xs text-muted-foreground">
              {{ $t('monitor.ranking.description') }}
            </div>
          </div>
          <Server class="size-4 text-muted-foreground" />
        </div>

        <div v-if="topModels.length > 0" class="space-y-3.5">
          <div v-for="item in topModels" :key="item.modelId">
            <div class="mb-1.5 flex items-center justify-between gap-3 text-xs">
              <div class="flex min-w-0 items-center gap-2">
                <span class="truncate font-medium text-foreground">
                  {{ item.modelName || $t('models.card.unnamed') }}
                </span>
                <NTag :bordered="false" size="tiny">{{ item.provider }}</NTag>
              </div>
              <div
                class="flex shrink-0 items-center gap-3 tabular-nums text-muted-foreground"
              >
                <span class="hidden sm:inline">{{
                  $t('monitor.ranking.calls', {
                    count: formatNumber(item.callCount),
                  })
                }}</span>
                <span>
                  {{ $t('monitor.chart.tokensLegend') }}
                  <span class="font-medium text-foreground">{{
                    formatNumber(item.tokenCount)
                  }}</span>
                </span>
                <span>{{ formatDuration(item.avgDuration) }}</span>
                <span
                  v-if="Number(item.errorCount ?? 0) > 0"
                  class="text-destructive"
                >
                  {{ $t('monitor.ranking.failed', { count: item.errorCount }) }}
                </span>
              </div>
            </div>
            <NProgress
              :percentage="
                Math.max(
                  2,
                  Math.round(
                    (Number(item.tokenCount ?? 0) / maxModelToken) * 100,
                  ),
                )
              "
              :show-indicator="false"
              :height="6"
              color="hsl(var(--primary))"
            />
          </div>
        </div>
        <LcEmptyState
          v-else
          class="py-2"
          :description="$t('monitor.ranking.description')"
          :title="$t('monitor.chart.empty')"
        />
      </div>

      <!-- 最近调用 -->
      <div
        class="mt-3 rounded-lg border border-border bg-card p-4 shadow-[var(--shadow-card)]"
      >
        <div class="mb-3">
          <div class="text-sm font-semibold text-foreground">
            {{ $t('monitor.recent.title') }}
          </div>
          <div class="mt-0.5 text-xs text-muted-foreground">
            {{ $t('monitor.recent.description') }}
          </div>
        </div>
        <NDataTable
          v-if="recentRows.length > 0"
          :columns="recentColumns"
          :data="recentRows"
          :pagination="false"
          :scroll-x="920"
          :single-line="false"
          size="small"
        >
          <template #empty>
            <NEmpty :description="$t('monitor.chart.empty')" />
          </template>
        </NDataTable>
        <LcEmptyState
          v-else
          class="py-2"
          :description="$t('monitor.recent.description')"
          :title="$t('monitor.chart.empty')"
        />
      </div>
    </NSpin>

    <!-- 用量分析 -->
    <NSpin v-else :show="usageLoading">
      <!-- 配额 + 汇总指标 -->
      <div class="grid gap-3 xl:grid-cols-[1.15fr_2fr]">
        <div
          class="rounded-lg border border-border bg-card p-4 shadow-[var(--shadow-card)]"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <div class="text-sm font-semibold text-foreground">
                {{ $t('monitor.usage.quota.title') }}
              </div>
              <div class="mt-0.5 text-xs text-muted-foreground">
                {{ $t('monitor.usage.quota.hint') }}
              </div>
            </div>
            <NTag
              :bordered="false"
              :type="
                quotaLimit <= 0
                  ? 'info'
                  : quotaPercent >= 90
                    ? 'error'
                    : quotaPercent >= 70
                      ? 'warning'
                      : 'primary'
              "
              round
              size="small"
            >
              {{
                quotaLimit <= 0
                  ? $t('monitor.usage.quota.unlimited')
                  : $t('monitor.usage.quota.percent', {
                      percent: quotaPercentText,
                    })
              }}
            </NTag>
          </div>
          <div class="mt-4 flex items-end justify-between gap-3">
            <div>
              <div class="text-[10px] text-muted-foreground">
                {{ $t('monitor.usage.quota.today') }}
              </div>
              <div
                class="c-metric-value mt-1 text-[26px] font-semibold leading-none tracking-tight text-foreground"
              >
                {{ formatNumber(quota.todayTokens) }}
              </div>
            </div>
            <div class="text-right">
              <div class="text-[10px] text-muted-foreground">
                {{ $t('monitor.usage.quota.limit') }}
              </div>
              <div class="mt-1 text-xs font-medium text-foreground">
                {{
                  quotaLimit <= 0
                    ? $t('monitor.usage.quota.unlimited')
                    : formatNumber(quotaLimit)
                }}
              </div>
            </div>
          </div>
          <NProgress
            class="mt-3"
            :percentage="quotaLimit <= 0 ? 0 : quotaPercent"
            :show-indicator="false"
            :height="10"
            type="line"
          />
          <div class="mt-2 text-[10px] text-muted-foreground">
            {{
              quotaLimit <= 0
                ? $t('monitor.usage.quota.noLimitHint')
                : $t('monitor.usage.quota.percent', {
                    percent: quotaPercentText,
                  })
            }}
          </div>
        </div>

        <div class="grid gap-3 sm:grid-cols-2">
          <div
            v-for="card in usageMetricCards"
            :key="card.key"
            class="rounded-lg border border-border bg-card p-4 shadow-[var(--shadow-card)]"
          >
            <div class="flex items-center justify-between gap-2">
              <div class="truncate text-[13px] font-semibold text-foreground">
                {{ card.label }}
              </div>
              <component
                :is="card.icon"
                class="size-4 shrink-0 text-muted-foreground"
              />
            </div>
            <div
              class="c-metric-value mt-3 text-[24px] font-semibold leading-none tracking-tight text-foreground"
            >
              {{ card.value }}
            </div>
            <div class="mt-1.5 truncate text-[10px] text-muted-foreground">
              {{ card.hint }}
            </div>
          </div>
        </div>
      </div>

      <!-- 用量趋势 -->
      <div
        class="mt-3 rounded-lg border border-border bg-card p-4 shadow-[var(--shadow-card)]"
      >
        <div class="mb-3 flex items-start justify-between gap-3">
          <div>
            <div class="text-sm font-semibold text-foreground">
              {{ $t('monitor.usage.trend.title') }}
            </div>
            <div class="mt-0.5 text-xs text-muted-foreground">
              {{ $t('monitor.usage.trend.description') }}
            </div>
          </div>
          <span
            class="hidden shrink-0 items-center gap-1 rounded-md border border-border bg-muted/30 px-2 py-1 text-[10px] text-muted-foreground sm:inline-flex"
          >
            <Activity class="size-3" />
            {{ $t('monitor.usage.totals.calls') }} /
            {{ $t('monitor.usage.totals.tokens') }}
          </span>
        </div>
        <EchartsUI ref="usageTrendChartRef" height="300px" />
      </div>

      <!-- 分布图 -->
      <div class="mt-3 grid gap-3 lg:grid-cols-2">
        <div
          class="rounded-lg border border-border bg-card p-4 shadow-[var(--shadow-card)]"
        >
          <div class="mb-1">
            <div class="text-sm font-semibold text-foreground">
              {{ $t('monitor.usage.scene.title') }}
            </div>
            <div class="mt-0.5 text-xs text-muted-foreground">
              {{ $t('monitor.usage.scene.description') }}
            </div>
          </div>
          <EchartsUI ref="usageSceneChartRef" height="260px" />
        </div>

        <div
          class="rounded-lg border border-border bg-card p-4 shadow-[var(--shadow-card)]"
        >
          <div class="mb-1">
            <div class="text-sm font-semibold text-foreground">
              {{ $t('monitor.usage.callType.title') }}
            </div>
            <div class="mt-0.5 text-xs text-muted-foreground">
              {{ $t('monitor.usage.callType.description') }}
            </div>
          </div>
          <EchartsUI ref="usageCallTypeChartRef" height="260px" />
        </div>
      </div>

      <!-- 按模型用量 -->
      <div
        class="mt-3 rounded-lg border border-border bg-card p-4 shadow-[var(--shadow-card)]"
      >
        <div class="mb-3">
          <div class="text-sm font-semibold text-foreground">
            {{ $t('monitor.usage.byModel.title') }}
          </div>
          <div class="mt-0.5 text-xs text-muted-foreground">
            {{ $t('monitor.usage.byModel.description') }}
          </div>
        </div>
        <NDataTable
          :bordered="false"
          :columns="usageModelColumns"
          :data="usageByModel"
          :pagination="false"
          :scroll-x="820"
          :single-line="false"
          size="small"
        >
          <template #empty>
            <NEmpty :description="$t('monitor.usage.byModel.empty')" />
          </template>
        </NDataTable>
      </div>

      <!-- 最近调用明细（服务端分页） -->
      <div
        class="mt-3 rounded-lg border border-border bg-card p-4 shadow-[var(--shadow-card)]"
      >
        <div class="mb-3">
          <div class="text-sm font-semibold text-foreground">
            {{ $t('monitor.usage.recent.title') }}
          </div>
          <div class="mt-0.5 text-xs text-muted-foreground">
            {{ $t('monitor.usage.recent.description') }}
          </div>
        </div>
        <NDataTable
          v-if="usageRecentRows.length > 0"
          :bordered="false"
          :columns="recentColumns"
          :data="usageRecentRows"
          :pagination="false"
          :scroll-x="920"
          :single-line="false"
          size="small"
        />
        <LcEmptyState
          v-else
          class="py-2"
          :description="$t('monitor.usage.recent.empty')"
          :title="$t('monitor.chart.empty')"
        />
        <div
          class="mt-2 flex flex-wrap items-center justify-between gap-2 border-t border-border/60 pt-2.5"
        >
          <span class="text-[11px] text-muted-foreground">
            {{ $t('monitor.usage.recent.total', { total: usageRecentTotal }) }}
          </span>
          <NPagination
            :item-count="usageRecentTotal"
            :page="usagePageNo"
            :page-size="usagePageSize"
            :page-sizes="[10, 20, 50]"
            show-size-picker
            size="small"
            @update:page="handleUsagePageChange"
            @update:page-size="handleUsagePageSizeChange"
          />
        </div>
      </div>
    </NSpin>
  </Page>
</template>

<style scoped>
.c-metric-value {
  font-family:
    'Inter', 'SF Mono', 'JetBrains Mono', ui-monospace, 'Cascadia Mono',
    'Cascadia Code', Consolas, monospace;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}
</style>
