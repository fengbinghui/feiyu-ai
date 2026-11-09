package cn.langchat.starter.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.langchat.common.core.ApiResponse;
import cn.langchat.common.core.CommonErrorCode;
import cn.langchat.common.exception.BizException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** 登录失败风控错误码前缀，映射为 HTTP 429。 */
    private static final String RATE_LIMIT_CODE_PREFIX = "AUTH_LOGIN_RATE_LIMIT";

    private static final String FORBIDDEN_CODE_PREFIX = "FORBIDDEN";
    private static final String AUTH_CODE_PREFIX = "AUTH_";

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException exception) {
        HttpStatus status = resolveHttpStatus(exception.getCode());
        log.info("业务异常响应，code={}, message={}", exception.getCode(), exception.getMessage(), exception);
        return ResponseEntity.status(status).body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? CommonErrorCode.BAD_REQUEST.message() : fieldError.getDefaultMessage();
        log.info("参数校验失败，message={}", message);
        return ResponseEntity.badRequest().body(ApiResponse.failure(CommonErrorCode.BAD_REQUEST.code(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException exception) {
        log.info("约束校验失败，message={}", exception.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.failure(CommonErrorCode.BAD_REQUEST.code(), exception.getMessage()));
    }

    /**
     * 请求体不是合法 JSON / 类型无法反序列化：返回 400，而不是笼统的 500。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException exception) {
        log.info("请求体解析失败，message={}", exception.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(CommonErrorCode.BAD_REQUEST.code(), "请求体格式错误，请提交合法的 JSON"));
    }

    /**
     * 查询参数类型不匹配（例如 pageNo=abc）：返回 400。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.info("请求参数类型不匹配，name={}, value={}", exception.getName(), exception.getValue());
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(CommonErrorCode.BAD_REQUEST.code(), "请求参数类型错误：" + exception.getName()));
    }

    /**
     * 缺少必填查询参数：返回 400。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException exception) {
        log.info("缺少必填请求参数，name={}", exception.getParameterName());
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(CommonErrorCode.BAD_REQUEST.code(), "缺少必填参数：" + exception.getParameterName()));
    }

    /**
     * 无权限 / 无角色：返回 403，而不是笼统的 401。
     */
    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotPermissionException(RuntimeException exception) {
        log.info("权限校验失败，message={}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure(CommonErrorCode.FORBIDDEN));
    }

    @ExceptionHandler({NotLoginException.class, SaTokenException.class})
    public ResponseEntity<ApiResponse<Void>> handleSaTokenException(RuntimeException exception) {
        log.info("认证异常，message={}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(CommonErrorCode.UNAUTHORIZED));
    }

    /**
     * 不支持的 HTTP 方法（例如对只读日志接口发起 POST/PUT/DELETE）：返回 405。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        log.info("请求方法不支持，message={}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.failure("METHOD_NOT_ALLOWED", "请求方法不支持"));
    }

    /**
     * 静态资源/接口路径不存在：返回 404，而不是 500。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException exception) {
        log.info("请求资源不存在，message={}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("NOT_FOUND", "请求资源不存在"));
    }

    /**
     * 兜底异常：对外只返回通用文案，避免泄露堆栈/内部实现细节；requestId 仍随响应返回便于排查。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("系统异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(CommonErrorCode.INTERNAL_ERROR.code(), CommonErrorCode.INTERNAL_ERROR.message()));
    }

    /**
     * 业务错误码到 HTTP 状态码的映射：
     * 登录风控 → 429；FORBIDDEN_ → 403；AUTH_ → 401；其余 → 400。
     */
    private HttpStatus resolveHttpStatus(String code) {
        if (code == null || code.isBlank()) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code.startsWith(RATE_LIMIT_CODE_PREFIX)) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (code.startsWith(FORBIDDEN_CODE_PREFIX)) {
            return HttpStatus.FORBIDDEN;
        }
        if (code.startsWith(AUTH_CODE_PREFIX)) {
            return HttpStatus.UNAUTHORIZED;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
