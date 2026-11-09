package cn.langchat.core.chat.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 会话标题更新请求对象。
 *
 * @param title 标题（1-100 字）
 * @author LangChat Team
 * @since 2026/11/9
 */
public record ConversationTitleRequest(
        @NotBlank(message = "title 不能为空")
        @Size(min = 1, max = 100, message = "title 长度需为 1-100 位")
        String title
) {
}
