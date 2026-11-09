import { requestClient } from '#/api/request';

/**
 * 基础实体类型。
 */
export interface BaseEntity {
  createTime?: number;
  creator?: string;
  id?: string;
  updateTime?: number;
  updater?: string;
}

/**
 * 服务端分页响应（与后端 PageResult 结构一致）。
 */
export interface PageResult<T> {
  /** 页码（从 1 开始） */
  pageNo: number;
  /** 页大小 */
  pageSize: number;
  /** 当前页记录 */
  records: T[];
  /** 总记录数 */
  total: number;
}

/**
 * 分页查询参数（pageNo/pageSize + 资源自有过滤条件）。
 */
export type PageParams = Record<
  string,
  boolean | null | number | string | undefined
>;

export function createCrudApi<T extends BaseEntity>(basePath: string) {
  return {
    create: (payload: Partial<T>) =>
      requestClient.post<boolean>(basePath, payload),
    detail: (id: string) => requestClient.get<T>(`${basePath}/${id}`),
    list: (params?: Record<string, number | string | undefined>) =>
      requestClient.get<T[]>(basePath, { params }),
    /**
     * 服务端分页列表：GET {basePath}/page
     */
    page: (params?: PageParams) =>
      requestClient.get<PageResult<T>>(`${basePath}/page`, { params }),
    remove: (id: string) => requestClient.delete<boolean>(`${basePath}/${id}`),
    update: (id: string, payload: Partial<T>) =>
      requestClient.put<boolean>(`${basePath}/${id}`, payload),
  };
}
