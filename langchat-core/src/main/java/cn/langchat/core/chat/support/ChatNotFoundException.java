package cn.langchat.core.chat.support;

/**
 * 对话资源不存在异常（映射为 HTTP 404）。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
public class ChatNotFoundException extends RuntimeException {

    public ChatNotFoundException(String message) {
        super(message);
    }
}
