package cn.langchat.core.chat.model.response;

/**
 * 会话分享创建/查询响应对象。
 *
 * @param shareId 分享 ID（UUID 无横线）
 * @author LangChat Team
 * @since 2026/11/9
 */
public record ConversationShareResponse(String shareId) {
}
