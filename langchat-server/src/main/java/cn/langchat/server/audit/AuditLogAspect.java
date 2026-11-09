package cn.langchat.server.audit;

import cn.langchat.aigc.biz.entity.AigcLog;
import cn.langchat.aigc.biz.service.AigcLogService;
import cn.langchat.auth.domain.model.AuthUserProfile;
import cn.langchat.auth.service.AuthService;
import cn.langchat.common.auth.AuthUtil;
import cn.langchat.common.core.audit.AuditLog;
import cn.langchat.common.core.audit.SensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

/**
 * 操作审计日志切面。
 *
 * <p>拦截标注 {@link AuditLog} 的 Controller 方法，记录操作人、IP、User-Agent、
 * 请求方法/URL、耗时、参数（敏感字段脱敏）与执行结果，写入 {@code aigc_log}。
 * 审计写入失败只告警，绝不阻断业务请求；业务异常记录后原样抛出。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogAspect {

    /** aigc_log.params 列长度（varchar 255），写入前截断避免超长报错。 */
    private static final int MAX_PARAMS_LENGTH = 255;
    /** aigc_log.operation 列长度（varchar 20）。 */
    private static final int MAX_OPERATION_LENGTH = 20;
    private static final int MAX_USERNAME_LENGTH = 20;
    private static final int MAX_URL_LENGTH = 1000;
    private static final int MAX_METHOD_LENGTH = 100;
    private static final int MAX_IP_LENGTH = 255;
    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 120;
    private static final int MAX_FORWARDED_IP_LENGTH = 64;
    private static final String ANONYMOUS = "anonymous";
    private static final String FAILED_SUFFIX = "（失败）";

    private final AigcLogService aigcLogService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startAt = System.currentTimeMillis();
        String params = buildParamsSafely(joinPoint);
        try {
            Object result = joinPoint.proceed();
            saveLog(joinPoint, auditLog, params, System.currentTimeMillis() - startAt, null);
            return result;
        } catch (Throwable throwable) {
            String failedParams = appendError(params, throwable);
            saveLog(joinPoint, auditLog, failedParams, System.currentTimeMillis() - startAt, throwable);
            throw throwable;
        }
    }

    private void saveLog(
            ProceedingJoinPoint joinPoint,
            AuditLog auditLog,
            String params,
            long costMs,
            Throwable error
    ) {
        try {
            HttpServletRequest request = currentRequest();
            AigcLog entity = new AigcLog();
            entity.setType(auditLog.type().name());
            entity.setUsername(truncate(resolveUsername(), MAX_USERNAME_LENGTH));
            String action = auditLog.action();
            if (error != null) {
                action = action + FAILED_SUFFIX;
            }
            entity.setOperation(truncate(action, MAX_OPERATION_LENGTH));
            entity.setUrl(request == null ? null : truncate(request.getRequestURI(), MAX_URL_LENGTH));
            entity.setMethod(request == null ? null : truncate(request.getMethod(), MAX_METHOD_LENGTH));
            entity.setTime(costMs);
            entity.setParams(truncate(params, MAX_PARAMS_LENGTH));
            entity.setIp(request == null ? null : truncate(resolveIp(request), MAX_IP_LENGTH));
            entity.setUserAgent(request == null
                    ? null
                    : truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH));
            aigcLogService.save(entity);
        } catch (Exception ex) {
            // 审计失败不影响业务
            log.warn("写入操作审计日志失败（忽略），action={}", auditLog.action(), ex);
        }
    }

    /**
     * 构造参数 JSON：参数名 -> 参数值（文件参数仅记录文件名与大小），敏感字段脱敏后截断。
     */
    private String buildParamsSafely(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] parameterNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            Map<String, Object> payload = new LinkedHashMap<>();
            if (args != null) {
                for (int index = 0; index < args.length; index++) {
                    String name = parameterNames != null && index < parameterNames.length
                            ? parameterNames[index]
                            : "arg" + index;
                    payload.put(name, sanitizeArgument(args[index]));
                }
            }
            String json = objectMapper.writeValueAsString(payload);
            return SensitiveDataMasker.mask(json);
        } catch (Exception ex) {
            log.warn("构建审计参数失败（使用空参数）", ex);
            return "[]";
        }
    }

    /**
     * 过滤不可/不应序列化的参数（Servlet 对象、流、文件），文件仅保留元信息。
     */
    private Object sanitizeArgument(Object argument) {
        if (argument == null) {
            return null;
        }
        if (argument instanceof MultipartFile file) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("filename", file.getOriginalFilename());
            meta.put("size", file.getSize());
            return meta;
        }
        if (argument instanceof MultipartFile[] files) {
            return "files[" + files.length + "]";
        }
        if (argument instanceof ServletRequest || argument instanceof ServletResponse
                || argument instanceof InputStream || argument instanceof OutputStream) {
            return null;
        }
        return argument;
    }

    /**
     * 失败时在参数 JSON 对象中补充 error 字段。
     */
    private String appendError(String params, Throwable throwable) {
        try {
            String message = throwable == null || throwable.getMessage() == null
                    ? "unknown error"
                    : throwable.getMessage();
            String safeMessage = message.length() > MAX_ERROR_MESSAGE_LENGTH
                    ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                    : message;
            safeMessage = safeMessage.replace("\\", "\\\\").replace("\"", "'");
            String base = params == null || params.isBlank() ? "{}" : params.trim();
            if (base.endsWith("}")) {
                return base.substring(0, base.length() - 1) + ",\"error\":\"" + safeMessage + "\"}";
            }
            return base + " error=" + safeMessage;
        } catch (Exception ex) {
            return params;
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                return attributes.getRequest();
            }
        } catch (Exception ex) {
            log.debug("获取当前请求失败（非 Web 上下文）", ex);
        }
        return null;
    }

    private String resolveUsername() {
        try {
            String userId = AuthUtil.getUserId();
            if (userId == null || userId.isBlank()) {
                return ANONYMOUS;
            }
            // 优先解析为可读用户名（如 admin），避免操作日志展示内部登录 ID（如 user_admin）。
            return authService.findByUserId(userId)
                    .map(AuthUserProfile::username)
                    .filter(name -> name != null && !name.isBlank())
                    .map(name -> truncate(name, MAX_USERNAME_LENGTH))
                    .orElse(userId);
        } catch (Exception ex) {
            return ANONYMOUS;
        }
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return truncate(first, MAX_FORWARDED_IP_LENGTH);
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
