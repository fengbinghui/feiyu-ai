package cn.langchat.core.chat.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建会话请求对象。
 *
 * @param agentId Agent ID（必填）
 * @param title 会话标题（可选，为空时自动生成「新会话 HH:mm」）
 * @author LangChat Team
 * @since 2026/11/9
 */
public record ConversationCreateRequest(
        @NotBlank(message = "agentId 不能为空")
        String agentId,

        @Size(max = 100, message = "title 长度不能超过 100 位")
        String title
) {
}
