package cn.langchat.core.chat.model.request;

/**
 * 消息反馈请求对象。
 *
 * <p>{@code likes=true} 表示点赞，{@code false} 表示点踩，{@code null} 表示取消反馈。</p>
 *
 * @param likes 反馈值
 * @author LangChat Team
 * @since 2026/11/9
 */
public record MessageFeedbackRequest(Boolean likes) {
}
