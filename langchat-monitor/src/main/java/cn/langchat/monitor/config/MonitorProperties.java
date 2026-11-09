package cn.langchat.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 监控与用量相关配置。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Data
@Component
@ConfigurationProperties(prefix = "langchat.monitor")
public class MonitorProperties {

    /**
     * 每日 Token 配额；0 或负数表示不限制。
     */
    private Long dailyTokenLimit = 0L;
}
