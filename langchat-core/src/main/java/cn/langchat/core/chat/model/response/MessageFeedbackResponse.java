package cn.langchat.core.chat.model.response;

/**
 * 消息反馈响应对象。
 *
 * @param messageId 消息 ID
 * @param likes 反馈值（true 点赞 / false 点踩 / null 取消）
 * @author LangChat Team
 * @since 2026/11/9
 */
public record MessageFeedbackResponse(
        String messageId,
        Boolean likes
) {
}
