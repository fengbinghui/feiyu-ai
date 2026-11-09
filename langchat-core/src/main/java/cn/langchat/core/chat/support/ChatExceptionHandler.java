package cn.langchat.core.chat.support;

import cn.langchat.common.core.ApiResponse;
import cn.langchat.common.core.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 对话资源异常处理器。
 *
 * <p>对话分享/反馈等接口需要精确的 404/403 语义，因此单独收敛为 {@link ChatNotFoundException}
 * 与 {@link ChatForbiddenException}；优先级高于全局兜底异常处理器。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ChatExceptionHandler {

    @ExceptionHandler(ChatNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ChatNotFoundException exception) {
        log.info("对话资源不存在，message={}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(ChatForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ChatForbiddenException exception) {
        log.info("对话资源无权访问，message={}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure(CommonErrorCode.FORBIDDEN.code(), exception.getMessage()));
    }
}
