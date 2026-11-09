import { requestClient } from '#/api/request';

/**
 * 操作日志（只读）。
 *
 * 后端字段：id / type / username / operation / url / time(耗时毫秒) /
 * method / params / ip / userAgent / createTime(操作时间)。
 */
export interface AigcLogItem {
  createTime?: number;
  id?: string;
  ip?: string;
  method?: string;
  operation?: string;
  params?: string;
  /** 耗时，单位毫秒 */
  time?: number;
  type?: string;
  url?: string;
  userAgent?: string;
  username?: string;
}

/**
 * 操作日志分页查询参数。
 */
export interface LogPageQuery {
  /** 结束时间（毫秒时间戳，含） */
  endTime?: number;
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
  /** 开始时间（毫秒时间戳，含） */
  startTime?: number;
  type?: string;
  username?: string;
}

/**
 * 操作日志分页结果。
 */
export interface LogPageResult {
  pageNo: number;
  pageSize: number;
  records: AigcLogItem[];
  total: number;
}

/**
 * 兼容后端不同阶段的分页返回：
 * - 标准 PageResult：{ records, total, pageNo, pageSize }
 * - 过渡期直接返回数组
 */
function normalizeLogPage(
  data: unknown,
  pageNo: number,
  pageSize: number,
): LogPageResult {
  if (Array.isArray(data)) {
    const records = data as AigcLogItem[];
    return { pageNo, pageSize, records, total: records.length };
  }
  const payload = (data ?? {}) as Record<string, unknown>;
  const rawRecords = Array.isArray(payload.records)
    ? (payload.records as AigcLogItem[])
    : [];
  return {
    pageNo: Number(payload.pageNo ?? pageNo),
    pageSize: Number(payload.pageSize ?? pageSize),
    records: rawRecords,
    total: Number(payload.total ?? rawRecords.length),
  };
}

/**
 * 分页查询操作日志。
 */
export async function getLogPageApi(query: LogPageQuery = {}) {
  const pageNo = query.pageNo ?? 1;
  const pageSize = query.pageSize ?? 20;
  const data = await requestClient.get<unknown>('/v1/aigc/logs', {
    params: {
      ...query,
      pageNo,
      pageSize,
    },
  });
  return normalizeLogPage(data, pageNo, pageSize);
}

/**
 * 查询操作日志详情。
 */
export function getLogDetailApi(id: string) {
  return requestClient.get<AigcLogItem>(`/v1/aigc/logs/${id}`);
}
