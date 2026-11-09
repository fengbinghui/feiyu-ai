package cn.langchat.server;

import cn.langchat.common.core.page.PageQuery;
import cn.langchat.common.core.page.PageResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分页工具单元测试（纯单元测试，不启动 Spring / 不连 DB）。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
class PageQueryTest {

    @Test
    void defaultsWhenParametersAreNull() {
        PageQuery query = PageQuery.of(null, null);
        assertEquals(PageQuery.DEFAULT_PAGE_NO, query.getPageNo());
        assertEquals(PageQuery.DEFAULT_PAGE_SIZE, query.getPageSize());
        assertEquals(0L, query.getOffset());
    }

    @Test
    void invalidPageNoFallsBackToOne() {
        assertEquals(1, PageQuery.of(0, 10).getPageNo());
        assertEquals(1, PageQuery.of(-5, 10).getPageNo());
    }

    @Test
    void invalidPageSizeFallsBackToDefault() {
        assertEquals(PageQuery.DEFAULT_PAGE_SIZE, PageQuery.of(1, 0).getPageSize());
        assertEquals(PageQuery.DEFAULT_PAGE_SIZE, PageQuery.of(1, -3).getPageSize());
    }

    @Test
    void pageSizeIsCappedAtMaximum() {
        assertEquals(PageQuery.MAX_PAGE_SIZE, PageQuery.of(1, 999).getPageSize());
        assertEquals(PageQuery.MAX_PAGE_SIZE, PageQuery.of(1, PageQuery.MAX_PAGE_SIZE).getPageSize());
    }

    @Test
    void offsetIsCalculatedCorrectly() {
        assertEquals(0L, PageQuery.of(1, 20).getOffset());
        assertEquals(40L, PageQuery.of(3, 20).getOffset());
        assertEquals(400L, PageQuery.of(3, 200).getOffset());
    }

    @Test
    void pageResultHandlesNullRecords() {
        PageResult<String> result = PageResult.of(null, null, 1, 20);
        assertNotNull(result.records());
        assertTrue(result.records().isEmpty());
        assertEquals(0L, result.total());
        assertEquals(1, result.pageNo());
        assertEquals(20, result.pageSize());
    }

    @Test
    void pageResultKeepsRecords() {
        PageResult<String> result = PageResult.of(List.of("a", "b"), 2L, 1, 2);
        assertEquals(2, result.records().size());
        assertEquals(2L, result.total());
    }
}
