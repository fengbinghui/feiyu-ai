package cn.langchat.core.chat.model.response;

import java.util.List;

/**
 * 公开会话分享只读视图。
 *
 * <p>不包含 creator / userId 等隐私字段，仅暴露展示所需信息。</p>
 *
 * @param title 会话标题
 * @param agentId Agent ID
 * @param agentName Agent 名称
 * @param agentAvatar Agent 头像
 * @param createdAt 会话创建时间（毫秒时间戳）
 * @param messages 消息列表（仅 user / assistant）
 * @author LangChat Team
 * @since 2026/11/9
 */
public record ChatShareResponse(
        String title,
        String agentId,
        String agentName,
        String agentAvatar,
        Long createdAt,
        List<ChatShareMessage> messages
) {

    /**
     * 分享消息视图。
     *
     * @param role 角色（user / assistant）
     * @param content 消息内容
     * @param createTime 消息时间（毫秒时间戳）
     */
    public record ChatShareMessage(
            String role,
            String content,
            Long createTime
    ) {
    }
}
