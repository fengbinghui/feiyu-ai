package cn.langchat.aigc.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.langchat.aigc.biz.entity.AigcLog;
import cn.langchat.aigc.biz.service.AigcLogService;
import cn.langchat.common.core.ApiResponse;
import cn.langchat.common.core.page.PageQuery;
import cn.langchat.common.core.page.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志控制器（只读：仅列表与详情）。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@RestController
@RequestMapping("/api/v1/aigc/logs")
@RequiredArgsConstructor
@Slf4j
@SaCheckPermission("langchat:log:list")
public class AigcLogController {

    private final AigcLogService aigcLogService;

    /**
     * 分页查询操作日志。
     *
     * @param pageNo 页码，默认 1
     * @param pageSize 页大小，默认 20、上限 200
     * @param type 日志类型（如 LOGIN / USER / MODEL / CHAT）
     * @param username 操作人（模糊匹配）
     * @param keyword 关键字（操作描述 / URL / 参数模糊匹配）
     * @param startTime 开始时间（毫秒时间戳，含）
     * @param endTime 结束时间（毫秒时间戳，含）
     */
    @GetMapping
    public ApiResponse<PageResult<AigcLog>> list(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startTime", required = false) Long startTime,
            @RequestParam(value = "endTime", required = false) Long endTime
    ) {
        PageQuery page = PageQuery.of(pageNo, pageSize);
        LambdaQueryWrapper<AigcLog> wrapper = buildQueryWrapper(type, username, keyword, startTime, endTime);
        long total = aigcLogService.count(wrapper);
        List<AigcLog> records = total <= 0
                ? List.of()
                : aigcLogService.list(wrapper.last(
                        "LIMIT " + page.getPageSize() + " OFFSET " + page.getOffset()
                ));
        return ApiResponse.success(PageResult.of(records, total, page.getPageNo(), page.getPageSize()));
    }

    @GetMapping("/{id}")
    public ApiResponse<AigcLog> detail(@PathVariable("id") String id) {
        return ApiResponse.success(aigcLogService.getById(id));
    }

    private LambdaQueryWrapper<AigcLog> buildQueryWrapper(
            String type,
            String username,
            String keyword,
            Long startTime,
            Long endTime
    ) {
        return Wrappers.<AigcLog>lambdaQuery()
                .eq(StringUtils.hasText(type), AigcLog::getType, type)
                .like(StringUtils.hasText(username), AigcLog::getUsername, username)
                .ge(startTime != null, AigcLog::getCreateTime, startTime)
                .le(endTime != null, AigcLog::getCreateTime, endTime)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(AigcLog::getOperation, keyword)
                        .or().like(AigcLog::getUsername, keyword)
                        .or().like(AigcLog::getUrl, keyword)
                        .or().like(AigcLog::getParams, keyword))
                .orderByDesc(AigcLog::getCreateTime);
    }
}
