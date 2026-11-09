package cn.langchat.server.audit;

import cn.langchat.aigc.biz.entity.AigcLog;
import cn.langchat.aigc.biz.service.AigcLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 审计日志异步写入器。
 *
 * <p>由 {@link AuditLogAspect} 在请求线程构造 {@link AigcLog} 实体后异步落库；
 * 写入失败仅记录告警，不影响业务请求。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogWriter {

    private final AigcLogService aigcLogService;

    /**
     * 异步保存审计日志。
     */
    @Async("auditLogExecutor")
    public void save(AigcLog auditLog) {
        if (auditLog == null) {
            return;
        }
        try {
            aigcLogService.save(auditLog);
        } catch (Exception ex) {
            log.warn("异步写入操作审计日志失败（忽略），type={}, operation={}",
                    auditLog.getType(), auditLog.getOperation(), ex);
        }
    }
}
