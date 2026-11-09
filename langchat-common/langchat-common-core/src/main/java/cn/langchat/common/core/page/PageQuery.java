package cn.langchat.common.core.page;

/**
 * 通用分页查询参数。
 *
 * <p>对页码与页大小做归一化：页码最小 1；页大小默认 20、最小 1、上限 200。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
public final class PageQuery {

    /** 默认页码。 */
    public static final int DEFAULT_PAGE_NO = 1;

    /** 默认页大小。 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 页大小上限。 */
    public static final int MAX_PAGE_SIZE = 200;

    private final int pageNo;
    private final int pageSize;

    public PageQuery(Integer pageNo, Integer pageSize) {
        this.pageNo = normalizePageNo(pageNo);
        this.pageSize = normalizePageSize(pageSize);
    }

    public static PageQuery of(Integer pageNo, Integer pageSize) {
        return new PageQuery(pageNo, pageSize);
    }

    public int getPageNo() {
        return pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }

    /**
     * 当前页偏移量（用于 LIMIT/OFFSET 查询）。
     */
    public long getOffset() {
        return (long) (pageNo - 1) * pageSize;
    }

    private static int normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return DEFAULT_PAGE_NO;
        }
        return pageNo;
    }

    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
