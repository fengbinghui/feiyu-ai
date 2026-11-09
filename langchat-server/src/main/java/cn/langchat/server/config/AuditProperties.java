package cn.langchat.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 审计日志配置。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Data
@Component
@ConfigurationProperties(prefix = "langchat.audit")
public class AuditProperties {

    /**
     * 审计日志保留天数（默认 90 天，定时任务删除更早的记录）。
     */
    private int retentionDays = 90;

    /**
     * 审计日志清理任务 cron 表达式（默认每天 03:30）。
     */
    private String retentionCron = "0 30 3 * * ?";
}
