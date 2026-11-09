package cn.langchat.monitor.model.response;

import cn.langchat.common.core.page.PageResult;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 模型用量分析响应。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Data
public class ModelCallUsageResponse {

    /** 统计窗口天数。 */
    private Integer windowDays;

    /** 配额信息。 */
    private Quota quota;

    /** 汇总指标。 */
    private Totals totals;

    /** 按天趋势。 */
    private List<TrendItem> trend = new ArrayList<>();

    /** 按模型统计。 */
    private List<ModelItem> byModel = new ArrayList<>();

    /** 按场景统计。 */
    private List<DistributionItem> byScene = new ArrayList<>();

    /** 按调用类型统计。 */
    private List<DistributionItem> byCallType = new ArrayList<>();

    /** 最近调用记录（分页）。 */
    private PageResult<ModelCallOverviewResponse.RecentCallItem> recentCalls;

    /**
     * 配额信息。
     */
    @Data
    public static class Quota {

        /** 每日 Token 配额（0 = 不限制）。 */
        private Long dailyTokenLimit;

        /** 今日 Token 消耗。 */
        private Long todayTokens;

        /** 配额使用百分比（limit>0 时计算，否则 null）。 */
        private Double usagePercent;
    }

    /**
     * 汇总指标。
     */
    @Data
    public static class Totals {

        /** 调用次数。 */
        private Long calls;

        /** Token 总消耗。 */
        private Long tokens;

        /** 输入 Token。 */
        private Long inputTokens;

        /** 输出 Token。 */
        private Long outputTokens;

        /** 失败次数。 */
        private Long errorCount;

        /** 成功率（百分比，无调用时 100）。 */
        private Double successRate;

        /** 平均耗时（毫秒）。 */
        private Long avgDuration;
    }

    /**
     * 按天趋势项。
     */
    @Data
    public static class TrendItem {

        /** 日期（MM-dd）。 */
        private String date;

        /** 调用次数。 */
        private Long calls;

        /** Token 消耗。 */
        private Long tokens;

        /** 失败次数。 */
        private Long errorCount;
    }

    /**
     * 按模型统计项。
     */
    @Data
    public static class ModelItem {

        /** 模型 ID。 */
        private String modelId;

        /** 模型名称。 */
        private String modelName;

        /** 供应商。 */
        private String provider;

        /** 调用次数。 */
        private Long calls;

        /** Token 消耗。 */
        private Long tokens;

        /** 失败次数。 */
        private Long errorCount;

        /** 平均耗时（毫秒）。 */
        private Long avgDuration;
    }

    /**
     * 分布项（场景 / 调用类型）。
     */
    @Data
    public static class DistributionItem {

        /** 维度名称。 */
        private String name;

        /** 调用次数。 */
        private Long calls;

        /** Token 消耗。 */
        private Long tokens;
    }
}
