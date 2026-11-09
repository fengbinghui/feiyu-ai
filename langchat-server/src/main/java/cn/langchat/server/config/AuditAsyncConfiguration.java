package cn.langchat.server.config;

import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 审计日志异步写入线程池配置。
 *
 * <p>审计日志写入与业务请求解耦：请求线程只负责构造日志实体，落库交给专用线程池。
 * 线程池采用 CallerRunsPolicy，队列满时回退到调用线程执行，避免日志丢失。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Configuration
@EnableScheduling
@Slf4j
public class AuditAsyncConfiguration {

    /**
     * 审计日志写入线程池。
     */
    @Bean(name = "auditLogExecutor")
    public ThreadPoolTaskExecutor auditLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("langchat-audit-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        log.info("初始化审计日志异步写入线程池，corePoolSize=1, maxPoolSize=4, queueCapacity=1000");
        return executor;
    }
}
