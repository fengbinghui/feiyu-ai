package cn.langchat.common.core.page;

import java.util.List;

/**
 * 通用分页响应结构。
 *
 * @param records 当前页记录
 * @param total 总记录数
 * @param pageNo 页码（从 1 开始）
 * @param pageSize 页大小
 * @param <T> 记录类型
 * @author LangChat Team
 * @since 2026/11/9
 */
public record PageResult<T>(
        List<T> records,
        Long total,
        Integer pageNo,
        Integer pageSize
) {

    /**
     * 构建分页响应（空记录兜底为空数组）。
     */
    public static <T> PageResult<T> of(List<T> records, Long total, Integer pageNo, Integer pageSize) {
        return new PageResult<>(
                records == null ? List.of() : records,
                total == null ? 0L : total,
                pageNo,
                pageSize
        );
    }
}
