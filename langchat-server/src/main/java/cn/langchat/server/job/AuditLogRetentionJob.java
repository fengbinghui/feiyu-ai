package cn.langchat.server.job;

import cn.langchat.aigc.biz.entity.AigcLog;
import cn.langchat.aigc.biz.service.AigcLogService;
import cn.langchat.server.config.AuditProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 操作审计日志保留清理任务。
 *
 * <p>按天删除 create_time 早于 {@code langchat.audit.retention-days}（默认 90 天）的记录，
 * 避免 aigc_log 无限增长。执行时间由 {@code langchat.audit.retention-cron} 配置。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogRetentionJob {

    private static final long ONE_DAY_MILLIS = 24L * 60 * 60 * 1000;

    private final AigcLogService aigcLogService;
    private final AuditProperties auditProperties;

    /**
     * 定时清理过期审计日志。
     */
    @Scheduled(cron = "${langchat.audit.retention-cron:0 30 3 * * ?}")
    public void cleanupExpiredLogs() {
        int retentionDays = Math.max(1, auditProperties.getRetentionDays());
        long cutoffTime = System.currentTimeMillis() - retentionDays * ONE_DAY_MILLIS;
        try {
            LambdaQueryWrapper<AigcLog> wrapper = Wrappers.<AigcLog>lambdaQuery()
                    .lt(AigcLog::getCreateTime, cutoffTime);
            long count = aigcLogService.count(wrapper);
            if (count <= 0) {
                log.info("审计日志清理完成，无过期记录，retentionDays={}", retentionDays);
                return;
            }
            boolean removed = aigcLogService.remove(wrapper);
            log.info("审计日志清理完成，retentionDays={}, deletedCount={}, removed={}",
                    retentionDays, count, removed);
        } catch (Exception ex) {
            log.error("审计日志清理失败（不影响服务），retentionDays={}", retentionDays, ex);
        }
    }
}
