package cn.langchat.server;

import cn.langchat.aigc.biz.entity.AigcLog;
import cn.langchat.aigc.biz.service.AigcLogService;
import cn.langchat.server.audit.AuditLogWriter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 审计日志异步写入器单元测试（不启动 Spring 容器、不连数据库）。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
class AuditLogWriterTest {

    @Test
    void savesAuditLogThroughService() {
        AigcLogService aigcLogService = mock(AigcLogService.class);
        AuditLogWriter writer = new AuditLogWriter(aigcLogService);
        AigcLog auditLog = new AigcLog();
        auditLog.setType("LOGIN");
        auditLog.setOperation("用户登录");

        writer.save(auditLog);

        verify(aigcLogService).save(auditLog);
    }

    @Test
    void exceptionIsNotPropagated() {
        AigcLogService aigcLogService = mock(AigcLogService.class);
        doThrow(new RuntimeException("db down")).when(aigcLogService).save(any(AigcLog.class));
        AuditLogWriter writer = new AuditLogWriter(aigcLogService);

        assertDoesNotThrow(() -> writer.save(new AigcLog()));
    }

    @Test
    void nullLogIsIgnored() {
        AigcLogService aigcLogService = mock(AigcLogService.class);
        AuditLogWriter writer = new AuditLogWriter(aigcLogService);

        writer.save(null);

        verifyNoInteractions(aigcLogService);
        verify(aigcLogService, never()).save(any(AigcLog.class));
    }
}
