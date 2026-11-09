package cn.langchat.server;

import cn.langchat.aigc.biz.service.AigcLogService;
import cn.langchat.server.config.AuditProperties;
import cn.langchat.server.job.AuditLogRetentionJob;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计日志保留清理任务单元测试（不启动 Spring 容器、不连数据库）。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
class AuditLogRetentionJobTest {

    @Test
    void deletesExpiredLogsWhenPresent() {
        AigcLogService aigcLogService = mock(AigcLogService.class);
        when(aigcLogService.count(any())).thenReturn(5L);
        when(aigcLogService.remove(any())).thenReturn(true);
        AuditLogRetentionJob job = new AuditLogRetentionJob(aigcLogService, properties(90));

        job.cleanupExpiredLogs();

        verify(aigcLogService).count(any());
        verify(aigcLogService).remove(any());
    }

    @Test
    void skipsDeleteWhenNoExpiredLogs() {
        AigcLogService aigcLogService = mock(AigcLogService.class);
        when(aigcLogService.count(any())).thenReturn(0L);
        AuditLogRetentionJob job = new AuditLogRetentionJob(aigcLogService, properties(30));

        job.cleanupExpiredLogs();

        verify(aigcLogService, never()).remove(any());
    }

    @Test
    void exceptionIsSwallowed() {
        AigcLogService aigcLogService = mock(AigcLogService.class);
        when(aigcLogService.count(any())).thenThrow(new RuntimeException("db down"));
        AuditLogRetentionJob job = new AuditLogRetentionJob(aigcLogService, properties(90));

        assertDoesNotThrow(job::cleanupExpiredLogs);
    }

    private AuditProperties properties(int retentionDays) {
        AuditProperties properties = new AuditProperties();
        properties.setRetentionDays(retentionDays);
        return properties;
    }
}
