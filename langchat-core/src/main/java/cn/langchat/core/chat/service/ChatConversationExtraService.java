package cn.langchat.core.chat.service;

import cn.langchat.aigc.biz.entity.AigcConversation;
import cn.langchat.core.chat.model.request.ConversationCreateRequest;
import cn.langchat.core.chat.model.response.ChatShareResponse;
import cn.langchat.core.chat.model.response.ConversationShareResponse;
import cn.langchat.core.chat.model.response.ConversationTitleResponse;
import cn.langchat.core.chat.model.response.MessageFeedbackResponse;

/**
 * 对话体验增强服务（会话 CRUD / 消息反馈 / 会话分享 / Markdown 导出 / 标题生成）。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
public interface ChatConversationExtraService {

    /**
     * 创建会话（creator=当前用户，title 为空时使用「新会话 HH:mm」）。
     */
    AigcConversation createConversation(ConversationCreateRequest request);

    /**
     * 删除会话并级联删除其消息与消息事件（仅 owner 可删除）。
     */
    boolean deleteConversation(String conversationId);

    /**
     * 消息反馈（点赞/点踩/取消）。
     */
    MessageFeedbackResponse feedback(String messageId, Boolean likes);

    /**
     * 创建会话分享（幂等，已有 shareId 时复用）。
     */
    ConversationShareResponse createShare(String conversationId);

    /**
     * 取消会话分享。
     */
    boolean removeShare(String conversationId);

    /**
     * 查询公开分享只读数据。
     */
    ChatShareResponse getShare(String shareId);

    /**
     * 导出会话为 Markdown 文本。
     */
    String exportMarkdown(String conversationId);

    /**
     * 更新会话标题。
     */
    boolean updateTitle(String conversationId, String title);

    /**
     * 基于首轮对话生成会话标题（模型失败时降级为截断标题）。
     */
    ConversationTitleResponse generateTitle(String conversationId);
}
