package cn.langchat.core.chat.support;

/**
 * 对话资源无权限异常（映射为 HTTP 403）。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
public class ChatForbiddenException extends RuntimeException {

    public ChatForbiddenException(String message) {
        super(message);
    }
}
