package cn.langchat.monitor.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.langchat.common.core.ApiResponse;
import cn.langchat.common.core.page.PageQuery;
import cn.langchat.monitor.model.response.ModelCallOverviewResponse;
import cn.langchat.monitor.model.response.ModelCallUsageResponse;
import cn.langchat.monitor.service.ModelCallStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型调用监控控制器。
 *
 * @author LangChat Team
 * @since 2026/9/26
 */
@RestController
@RequestMapping("/api/v1/monitor/model-calls")
@RequiredArgsConstructor
@Slf4j
@SaCheckPermission("aigc:monitor:model-call")
public class ModelCallMonitorController {

    private final ModelCallStatisticsService modelCallStatisticsService;

    /**
     * 查询模型调用总览。
     *
     * @param windowDays 统计窗口天数，默认 7 天
     */
    @GetMapping("/overview")
    public ApiResponse<ModelCallOverviewResponse> overview(
            @RequestParam(value = "windowDays", required = false) Integer windowDays
    ) {
        return ApiResponse.success(modelCallStatisticsService.getOverview(windowDays));
    }

    /**
     * 查询用量分析（配额 / 汇总 / 趋势 / 模型 / 场景 / 最近调用分页）。
     *
     * @param windowDays 统计窗口天数，默认 7 天
     * @param pageNo 最近调用记录页码，默认 1
     * @param pageSize 最近调用记录页大小，默认 20、上限 200
     */
    @GetMapping("/usage")
    public ApiResponse<ModelCallUsageResponse> usage(
            @RequestParam(value = "windowDays", required = false) Integer windowDays,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize
    ) {
        return ApiResponse.success(modelCallStatisticsService.getUsage(
                windowDays,
                PageQuery.of(pageNo, pageSize)
        ));
    }
}
