package cn.langchat.core.chat.service.impl;

import cn.langchat.aigc.biz.entity.AigcAgent;
import cn.langchat.aigc.biz.entity.AigcConversation;
import cn.langchat.aigc.biz.entity.AigcMessage;
import cn.langchat.aigc.biz.entity.AigcMessageEvent;
import cn.langchat.aigc.biz.service.AigcAgentService;
import cn.langchat.aigc.biz.service.AigcConversationService;
import cn.langchat.aigc.biz.service.AigcMessageEventService;
import cn.langchat.aigc.biz.service.AigcMessageService;
import cn.langchat.common.auth.AuthUtil;
import cn.langchat.common.core.CommonErrorCode;
import cn.langchat.common.exception.BizException;
import cn.langchat.core.chat.enums.ChatRoleEnum;
import cn.langchat.core.chat.model.request.ConversationCreateRequest;
import cn.langchat.core.chat.model.response.ChatShareResponse;
import cn.langchat.core.chat.model.response.ConversationShareResponse;
import cn.langchat.core.chat.model.response.ConversationTitleResponse;
import cn.langchat.core.chat.model.response.MessageFeedbackResponse;
import cn.langchat.core.chat.service.ChatConversationExtraService;
import cn.langchat.core.chat.support.ChatForbiddenException;
import cn.langchat.core.chat.support.ChatNotFoundException;
import cn.langchat.core.runtime.AgentRuntimeLoader;
import cn.langchat.core.runtime.factory.LangChain4jModelFactory;
import cn.langchat.core.runtime.model.AgentRuntimeDefinition;
import cn.langchat.core.support.CoreErrorCode;
import cn.langchat.monitor.model.ModelCallScene;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 对话体验增强服务实现。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatConversationExtraServiceImpl implements ChatConversationExtraService {

    /** AI 标题生成超时（秒）。 */
    private static final long TITLE_MODEL_TIMEOUT_SECONDS = 30L;
    /** AI 标题最大长度（汉字数）。 */
    private static final int MAX_AI_TITLE_LENGTH = 16;
    /** 降级标题最大长度。 */
    private static final int MAX_FALLBACK_TITLE_LENGTH = 20;
    /** 手工标题最大长度。 */
    private static final int MAX_MANUAL_TITLE_LENGTH = 100;
    private static final String DEFAULT_TITLE = "新对话";
    /** 新建会话默认标题前缀（后接 HH:mm）。 */
    private static final String NEW_CONVERSATION_TITLE_PREFIX = "新会话 ";
    private static final DateTimeFormatter NEW_CONVERSATION_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String TITLE_SYSTEM_PROMPT =
            "你是会话标题生成助手。请根据用户与助手的第一轮对话，概括一个不超过 16 个汉字的中文标题。"
                    + "要求：只输出标题本身，不要引号、不要标点前缀、不要任何解释或换行。";
    private static final String TITLE_USER_PROMPT_TEMPLATE = "用户：%s\n助手：%s";

    private final AigcConversationService aigcConversationService;
    private final AigcMessageService aigcMessageService;
    private final AigcMessageEventService aigcMessageEventService;
    private final AigcAgentService aigcAgentService;
    private final AgentRuntimeLoader agentRuntimeLoader;
    private final LangChain4jModelFactory langChain4jModelFactory;

    @Override
    public AigcConversation createConversation(ConversationCreateRequest request) {
        String userId = requireUserId();
        String agentId = request == null ? null : request.agentId();
        if (!StringUtils.hasText(agentId)) {
            throw new BizException(CoreErrorCode.INVALID_CHAT_REQUEST.code(), "agentId 不能为空");
        }
        AigcAgent agent = aigcAgentService.getById(agentId.trim());
        if (agent == null) {
            throw new BizException(CoreErrorCode.AGENT_NOT_FOUND.code(), "Agent 不存在或已禁用");
        }
        AigcConversation conversation = new AigcConversation();
        conversation.setAgentId(agent.getId());
        conversation.setCreator(userId);
        conversation.setUpdater(userId);
        conversation.setShareId(null);
        conversation.setTitle(resolveNewConversationTitle(request.title()));
        aigcConversationService.save(conversation);
        log.info("创建会话成功，conversationId={}, agentId={}, creator={}", conversation.getId(), agent.getId(), userId);
        return conversation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteConversation(String conversationId) {
        AigcConversation conversation = requireOwnedConversation(conversationId);
        // 级联删除消息事件与消息，避免会话删除后留下孤儿数据。
        aigcMessageEventService.lambdaUpdate()
                .eq(AigcMessageEvent::getConversationId, conversation.getId())
                .remove();
        aigcMessageService.lambdaUpdate()
                .eq(AigcMessage::getConversationId, conversation.getId())
                .remove();
        boolean removed = aigcConversationService.removeById(conversation.getId());
        log.info("删除会话完成（已级联删除消息与消息事件），conversationId={}, removed={}", conversationId, removed);
        return removed;
    }

    /**
     * 新建会话标题：显式传入则校验长度后使用，否则使用「新会话 HH:mm」。
     */
    private String resolveNewConversationTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return NEW_CONVERSATION_TITLE_PREFIX + LocalTime.now().format(NEW_CONVERSATION_TIME_FORMATTER);
        }
        String normalized = title.trim();
        if (normalized.length() > MAX_MANUAL_TITLE_LENGTH) {
            throw new BizException(CoreErrorCode.INVALID_CHAT_REQUEST.code(), "标题长度不能超过 100 位");
        }
        return normalized;
    }

    @Override
    public MessageFeedbackResponse feedback(String messageId, Boolean likes) {
        String userId = requireUserId();
        AigcMessage message = StringUtils.hasText(messageId) ? aigcMessageService.getById(messageId) : null;
        if (message == null) {
            throw new ChatNotFoundException("消息不存在");
        }
        AigcConversation conversation = aigcConversationService.getById(message.getConversationId());
        if (conversation == null) {
            throw new ChatNotFoundException("会话不存在");
        }
        if (!Objects.equals(userId, conversation.getCreator())) {
            throw new ChatForbiddenException("无权操作该消息");
        }
        aigcMessageService.lambdaUpdate()
                .eq(AigcMessage::getId, messageId)
                .set(AigcMessage::getLikes, likes)
                .update();
        log.info("消息反馈已更新，messageId={}, likes={}", messageId, likes);
        return new MessageFeedbackResponse(messageId, likes);
    }

    @Override
    public ConversationShareResponse createShare(String conversationId) {
        AigcConversation conversation = requireOwnedConversation(conversationId);
        String shareId = conversation.getShareId();
        if (!StringUtils.hasText(shareId)) {
            shareId = UUID.randomUUID().toString().replace("-", "");
            aigcConversationService.lambdaUpdate()
                    .eq(AigcConversation::getId, conversationId)
                    .set(AigcConversation::getShareId, shareId)
                    .update();
            log.info("会话分享已创建，conversationId={}, shareId={}", conversationId, shareId);
        }
        return new ConversationShareResponse(shareId);
    }

    @Override
    public boolean removeShare(String conversationId) {
        requireOwnedConversation(conversationId);
        aigcConversationService.lambdaUpdate()
                .eq(AigcConversation::getId, conversationId)
                .set(AigcConversation::getShareId, null)
                .update();
        log.info("会话分享已取消，conversationId={}", conversationId);
        return true;
    }

    @Override
    public ChatShareResponse getShare(String shareId) {
        if (!StringUtils.hasText(shareId)) {
            throw new ChatNotFoundException("分享不存在或已取消");
        }
        AigcConversation conversation = aigcConversationService.lambdaQuery()
                .eq(AigcConversation::getShareId, shareId)
                .last("LIMIT 1")
                .one();
        if (conversation == null) {
            throw new ChatNotFoundException("分享不存在或已取消");
        }
        AigcAgent agent = StringUtils.hasText(conversation.getAgentId())
                ? aigcAgentService.getById(conversation.getAgentId())
                : null;
        return new ChatShareResponse(
                conversation.getTitle(),
                conversation.getAgentId(),
                agent == null ? null : agent.getAgentName(),
                agent == null ? null : agent.getAvatar(),
                conversation.getCreateTime(),
                listVisibleMessages(conversation.getId()).stream()
                        .map(message -> new ChatShareResponse.ChatShareMessage(
                                message.getRole(),
                                message.getMessage(),
                                message.getCreateTime()
                        ))
                        .toList()
        );
    }

    @Override
    public String exportMarkdown(String conversationId) {
        AigcConversation conversation = requireOwnedConversation(conversationId);
        AigcAgent agent = StringUtils.hasText(conversation.getAgentId())
                ? aigcAgentService.getById(conversation.getAgentId())
                : null;
        String title = StringUtils.hasText(conversation.getTitle()) ? conversation.getTitle() : "未命名会话";
        String agentName = agent == null || !StringUtils.hasText(agent.getAgentName())
                ? "未知应用"
                : agent.getAgentName();
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(title).append("\n\n");
        markdown.append("- 应用名称：").append(agentName).append('\n');
        markdown.append("- 导出时间：").append(LocalDateTime.now().format(EXPORT_TIME_FORMATTER)).append("\n\n");
        markdown.append("---\n\n");
        for (AigcMessage message : listVisibleMessages(conversationId)) {
            String roleLabel = ChatRoleEnum.USER.code().equalsIgnoreCase(message.getRole()) ? "用户" : "助手";
            markdown.append("### ").append(roleLabel).append("\n\n");
            markdown.append(message.getMessage() == null ? "" : message.getMessage()).append("\n\n");
        }
        log.info("会话导出 Markdown 完成，conversationId={}, messageCount={}", conversationId,
                listVisibleMessages(conversationId).size());
        return markdown.toString();
    }

    @Override
    public boolean updateTitle(String conversationId, String title) {
        requireOwnedConversation(conversationId);
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_MANUAL_TITLE_LENGTH) {
            throw new BizException(CoreErrorCode.INVALID_CHAT_REQUEST.code(), "标题长度需为 1-100 位");
        }
        aigcConversationService.lambdaUpdate()
                .eq(AigcConversation::getId, conversationId)
                .set(AigcConversation::getTitle, normalized)
                .update();
        log.info("会话标题已更新，conversationId={}, titleLength={}", conversationId, normalized.length());
        return true;
    }

    @Override
    public ConversationTitleResponse generateTitle(String conversationId) {
        AigcConversation conversation = requireOwnedConversation(conversationId);
        List<AigcMessage> messages = listVisibleMessages(conversationId);
        if (messages.size() < 2) {
            throw new BizException(CoreErrorCode.INVALID_CHAT_REQUEST.code(), "消息不足 2 条，无法生成标题");
        }
        String userContent = firstMessageContent(messages, ChatRoleEnum.USER);
        String assistantContent = firstMessageContent(messages, ChatRoleEnum.ASSISTANT);
        String title = sanitizeGeneratedTitle(generateTitleByModel(conversation, userContent, assistantContent));
        if (!StringUtils.hasText(title)) {
            title = buildFallbackTitle(userContent);
        }
        aigcConversationService.lambdaUpdate()
                .eq(AigcConversation::getId, conversationId)
                .set(AigcConversation::getTitle, title)
                .update();
        log.info("会话标题生成完成，conversationId={}, title={}", conversationId, title);
        return new ConversationTitleResponse(title);
    }

    /**
     * 调用该会话 Agent 的对话模型生成标题；失败/为空返回 null 由调用方降级。
     */
    private String generateTitleByModel(AigcConversation conversation, String userContent, String assistantContent) {
        if (!StringUtils.hasText(conversation.getAgentId())) {
            return null;
        }
        try {
            AgentRuntimeDefinition runtime = agentRuntimeLoader.load(conversation.getAgentId());
            StreamingChatModel chatModel = langChain4jModelFactory.getStreamingChatModel(
                    runtime.chatModelConfig(), runtime.agent(), ModelCallScene.AGENT_CHAT);
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(TITLE_SYSTEM_PROMPT),
                            UserMessage.from(String.format(
                                    TITLE_USER_PROMPT_TEMPLATE,
                                    defaultText(userContent),
                                    defaultText(assistantContent)
                            ))
                    ))
                    .build();
            CompletableFuture<String> future = new CompletableFuture<>();
            chatModel.chat(request, new StreamingChatResponseHandler() {
                private final StringBuilder buffer = new StringBuilder();

                @Override
                public void onPartialResponse(String partialResponse) {
                    if (partialResponse != null) {
                        buffer.append(partialResponse);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    String text = response == null || response.aiMessage() == null
                            ? null
                            : response.aiMessage().text();
                    future.complete(StringUtils.hasText(text) ? text : buffer.toString());
                }

                @Override
                public void onError(Throwable error) {
                    future.completeExceptionally(error);
                }
            });
            String content = future.get(TITLE_MODEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return StringUtils.hasText(content) ? content : null;
        } catch (Exception ex) {
            log.warn("会话标题 AI 生成失败，将降级为截断标题，conversationId={}", conversation.getId(), ex);
            return null;
        }
    }

    /**
     * 清洗模型输出：去掉换行/引号/“标题：”前缀并截断到 16 字。
     */
    private String sanitizeGeneratedTitle(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String title = raw.replace("\r", " ").replace("\n", " ").trim();
        title = title.replaceAll("^(标题|题目|会话标题)\\s*[:：]\\s*", "");
        title = title.replaceAll("^[\"'“”‘’「」『』【】《》\\s]+", "");
        title = title.replaceAll("[\"'“”‘’「」『』【】《》\\s]+$", "");
        if (title.length() > MAX_AI_TITLE_LENGTH) {
            title = title.substring(0, MAX_AI_TITLE_LENGTH).trim();
        }
        return StringUtils.hasText(title) ? title : null;
    }

    /**
     * 降级标题：取首条用户消息单行截断（≤20 字）。
     */
    private String buildFallbackTitle(String userContent) {
        String content = defaultText(userContent).replaceAll("\\s+", " ").trim();
        if (content.isEmpty()) {
            return DEFAULT_TITLE;
        }
        return content.length() <= MAX_FALLBACK_TITLE_LENGTH
                ? content
                : content.substring(0, MAX_FALLBACK_TITLE_LENGTH).trim();
    }

    private String firstMessageContent(List<AigcMessage> messages, ChatRoleEnum role) {
        return messages.stream()
                .filter(message -> role.code().equalsIgnoreCase(message.getRole()))
                .map(AigcMessage::getMessage)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    /**
     * 会话可见消息：仅 user / assistant，按时间升序（不向分享/导出暴露 system/tool 等内部消息）。
     */
    private List<AigcMessage> listVisibleMessages(String conversationId) {
        return aigcMessageService.lambdaQuery()
                .eq(AigcMessage::getConversationId, conversationId)
                .orderByAsc(AigcMessage::getCreateTime)
                .list()
                .stream()
                .filter(message -> ChatRoleEnum.USER.code().equalsIgnoreCase(message.getRole())
                        || ChatRoleEnum.ASSISTANT.code().equalsIgnoreCase(message.getRole()))
                .toList();
    }

    private AigcConversation requireOwnedConversation(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new ChatNotFoundException("会话不存在");
        }
        AigcConversation conversation = aigcConversationService.getById(conversationId);
        if (conversation == null) {
            throw new ChatNotFoundException("会话不存在");
        }
        String userId = requireUserId();
        if (!Objects.equals(userId, conversation.getCreator())) {
            throw new ChatForbiddenException("无权访问该会话");
        }
        return conversation;
    }

    private String requireUserId() {
        String userId = AuthUtil.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }
}
