import { requestClient } from '#/api/request';

/**
 * 监控指标卡片。
 */
export interface ModelCallMetricCard {
  hint?: string;
  label?: string;
  tone?: string;
  value?: string;
}

/**
 * 调用趋势点。
 */
export interface ModelCallTrendPoint {
  callCount?: number;
  date?: string;
  tokenCount?: number;
}

/**
 * 分布项。
 */
export interface ModelCallDistributionItem {
  callCount?: number;
  name?: string;
  tokenCount?: number;
}

/**
 * 模型消耗项。
 */
export interface ModelCallUsageItem {
  avgDuration?: number;
  callCount?: number;
  errorCount?: number;
  modelId?: string;
  modelName?: string;
  provider?: string;
  tokenCount?: number;
}

/**
 * 最近调用记录。
 */
export interface ModelCallRecentItem {
  callType?: string;
  createTime?: number;
  duration?: number;
  modelName?: string;
  provider?: string;
  scene?: string;
  status?: string;
  totalToken?: number;
}

/**
 * 模型调用监控总览。
 */
export interface ModelCallOverview {
  callTypeDistribution?: ModelCallDistributionItem[];
  metrics?: ModelCallMetricCard[];
  providerDistribution?: ModelCallDistributionItem[];
  recentCalls?: ModelCallRecentItem[];
  sceneDistribution?: ModelCallDistributionItem[];
  topModels?: ModelCallUsageItem[];
  trend?: ModelCallTrendPoint[];
  windowDays?: number;
}

/**
 * 查询模型调用监控总览。
 */
export function getModelCallOverviewApi(windowDays?: number) {
  return requestClient.get<ModelCallOverview>('/v1/monitor/model-calls/overview', {
    params: { windowDays },
  });
}

/** 用量配额信息。 */
export interface ModelCallUsageQuota {
  /** 每日 Token 配额（0 = 不限制） */
  dailyTokenLimit?: number;
  /** 今日 Token 消耗 */
  todayTokens?: number;
  /** 配额使用百分比（limit > 0 时返回） */
  usagePercent?: number | null;
}

/** 用量汇总指标。 */
export interface ModelCallUsageTotals {
  avgDuration?: number;
  calls?: number;
  errorCount?: number;
  inputTokens?: number;
  outputTokens?: number;
  successRate?: number;
  tokens?: number;
}

/** 用量按天趋势项。 */
export interface ModelCallUsageTrendPoint {
  calls?: number;
  date?: string;
  errorCount?: number;
  tokens?: number;
}

/** 用量按模型统计项。 */
export interface ModelCallUsageModelItem {
  avgDuration?: number;
  calls?: number;
  errorCount?: number;
  modelId?: string;
  modelName?: string;
  provider?: string;
  tokens?: number;
}

/** 用量分布项（场景 / 调用类型）。 */
export interface ModelCallUsageDistributionItem {
  calls?: number;
  name?: string;
  tokens?: number;
}

/** 用量分析分页结果。 */
export interface ModelCallUsagePageResult<T> {
  pageNo?: number;
  pageSize?: number;
  records?: T[];
  total?: number;
}

/** 用量分析响应。 */
export interface ModelCallUsage {
  byCallType?: ModelCallUsageDistributionItem[];
  byModel?: ModelCallUsageModelItem[];
  byScene?: ModelCallUsageDistributionItem[];
  quota?: ModelCallUsageQuota;
  recentCalls?: ModelCallUsagePageResult<ModelCallRecentItem>;
  totals?: ModelCallUsageTotals;
  trend?: ModelCallUsageTrendPoint[];
  windowDays?: number;
}

/**
 * 查询模型用量分析（配额 / 汇总 / 趋势 / 分布 / 按模型 / 最近调用分页）。
 */
export function getModelCallUsageApi(params?: {
  pageNo?: number;
  pageSize?: number;
  windowDays?: number;
}) {
  return requestClient.get<ModelCallUsage>(
    '/v1/monitor/model-calls/usage',
    { params },
  );
}
