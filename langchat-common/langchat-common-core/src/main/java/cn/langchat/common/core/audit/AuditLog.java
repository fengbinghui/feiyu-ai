package cn.langchat.common.core.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计日志注解。
 *
 * <p>标注在 Controller 方法上，由 {@code AuditLogAspect} 切面统一记录操作人、IP、
 * 请求信息、耗时、参数（敏感字段脱敏）与执行结果，写入 {@code aigc_log} 表。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 操作类型。
     */
    AuditLogType type() default AuditLogType.OTHER;

    /**
     * 操作描述（建议不超过 20 个字符，受 aigc_log.operation 字段长度限制）。
     */
    String action();
}
