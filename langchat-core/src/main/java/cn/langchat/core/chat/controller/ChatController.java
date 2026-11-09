package cn.langchat.core.chat.controller;

import cn.langchat.aigc.biz.entity.AigcConversation;
import cn.langchat.aigc.biz.entity.AigcMessage;
import cn.langchat.aigc.biz.service.AigcConversationService;
import cn.langchat.aigc.biz.service.AigcMessageService;
import cn.langchat.common.auth.AuthUtil;
import cn.langchat.common.core.ApiResponse;
import cn.langchat.common.exception.BizException;
import cn.langchat.core.chat.enums.ChatRoleEnum;
import cn.langchat.core.chat.model.request.AgentChatStreamRequest;
import cn.langchat.core.chat.model.request.ChatCompletionMessage;
import cn.langchat.core.chat.model.request.ChatCompletionRequest;
import cn.langchat.core.chat.model.request.ConversationCreateRequest;
import cn.langchat.core.chat.model.request.ConversationTitleRequest;
import cn.langchat.core.chat.model.request.MessageFeedbackRequest;
import cn.langchat.core.chat.model.response.ChatShareResponse;
import cn.langchat.core.chat.model.response.ConversationShareResponse;
import cn.langchat.core.chat.model.response.ConversationTitleResponse;
import cn.langchat.core.chat.model.response.MessageFeedbackResponse;
import cn.langchat.core.chat.service.AgentChatRuntimeService;
import cn.langchat.core.chat.service.ChatConversationExtraService;
import cn.langchat.core.support.CoreErrorCode;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import org.springframework.http.codec.ServerSentEvent;

/**
 * 前端统一聊天控制器。
 *
 * @author LangChat Team
 * @since 2026/3/25
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private static final String MARKDOWN_CONTENT_TYPE = "text/markdown;charset=UTF-8";
    private static final int EXPORT_FILENAME_ID_LENGTH = 8;

    private final AigcConversationService aigcConversationService;
    private final AigcMessageService aigcMessageService;
    private final AgentChatRuntimeService agentChatRuntimeService;
    private final ChatConversationExtraService chatConversationExtraService;

    /**
     * 查询当前用户的会话列表。
     */
    @GetMapping("/conversations")
    public ApiResponse<List<AigcConversation>> listConversations(
            @RequestParam(value = "agentId", required = false) String agentId
    ) {
        String userId = AuthUtil.getUserId();
        log.info("查询统一会话列表，userId={}, agentId={}", userId, agentId);
        return ApiResponse.success(aigcConversationService.lambdaQuery()
                .eq(hasText(userId), AigcConversation::getCreator, userId)
                .eq(hasText(agentId), AigcConversation::getAgentId, agentId)
                .orderByDesc(AigcConversation::getUpdateTime)
                .orderByDesc(AigcConversation::getCreateTime)
                .list());
    }

    /**
     * 创建会话（creator=当前用户；title 为空时使用「新会话 HH:mm」）。
     */
    @PostMapping("/conversations")
    public ApiResponse<AigcConversation> createConversation(@Valid @RequestBody ConversationCreateRequest request) {
        log.info("创建会话请求，agentId={}", request.agentId());
        return ApiResponse.success(chatConversationExtraService.createConversation(request));
    }

    /**
     * 更新会话（兼容别名，仅支持更新 title，行为与 PUT /{id}/title 一致）。
     */
    @PutMapping("/conversations/{conversationId}")
    public ApiResponse<Boolean> updateConversation(
            @PathVariable("conversationId") String conversationId,
            @Valid @RequestBody ConversationTitleRequest request
    ) {
        log.info("更新会话请求，conversationId={}", conversationId);
        return ApiResponse.success(chatConversationExtraService.updateTitle(conversationId, request.title()));
    }

    /**
     * 删除会话（级联删除该会话的消息与消息事件，仅 owner 可删除）。
     */
    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Boolean> deleteConversation(@PathVariable("conversationId") String conversationId) {
        log.info("删除会话请求，conversationId={}", conversationId);
        return ApiResponse.success(chatConversationExtraService.deleteConversation(conversationId));
    }

    /**
     * 查询指定会话的消息列表。
     *
     * <p>会话不存在时返回空列表（例如首次进入 Agent 构建页，固定会话尚未创建）。</p>
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<AigcMessage>> listMessages(@PathVariable("conversationId") String conversationId) {
        if (!hasText(conversationId)) {
            throw new BizException(CoreErrorCode.INVALID_CHAT_REQUEST);
        }
        AigcConversation conversation = aigcConversationService.getById(conversationId);
        if (conversation == null) {
            return ApiResponse.success(List.of());
        }
        String userId = AuthUtil.getUserId();
        if (hasText(userId) && hasText(conversation.getCreator()) && !userId.equals(conversation.getCreator())) {
            throw new BizException(CoreErrorCode.CONVERSATION_NOT_FOUND);
        }
        log.info("查询统一会话消息，conversationId={}, agentId={}", conversationId, conversation.getAgentId());
        return ApiResponse.success(aigcMessageService.lambdaQuery()
                .eq(AigcMessage::getConversationId, conversationId)
                .orderByAsc(AigcMessage::getCreateTime)
                .list());
    }

    /**
     * 创建一次统一聊天补全流。
     */
    @PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> createCompletion(@RequestBody ChatCompletionRequest request) {
        String agentId = resolveAgentId(request);
        AgentChatStreamRequest streamRequest = new AgentChatStreamRequest();
        streamRequest.setAttachments(request.getAttachments());
        streamRequest.setConversationId(request.getConversationId());
        streamRequest.setVariables(request.getVariables());
        streamRequest.setMessage(resolveLatestUserMessage(request));
        log.info("创建统一聊天补全请求，agentId={}, conversationId={}, stream={}",
                agentId,
                request.getConversationId(),
                request.getStream());
        return agentChatRuntimeService.streamChat(agentId, streamRequest);
    }

    // ------------------------------------------------------------------
    // 阶段3b：消息反馈 / 会话分享 / Markdown 导出 / 会话标题
    // ------------------------------------------------------------------

    /**
     * 消息反馈：true 点赞、false 点踩、null 取消。
     */
    @PostMapping("/messages/{messageId}/feedback")
    public ApiResponse<MessageFeedbackResponse> feedback(
            @PathVariable("messageId") String messageId,
            @RequestBody(required = false) MessageFeedbackRequest request
    ) {
        log.info("消息反馈请求，messageId={}, likes={}", messageId, request == null ? null : request.likes());
        return ApiResponse.success(chatConversationExtraService.feedback(
                messageId,
                request == null ? null : request.likes()
        ));
    }

    /**
     * 创建会话分享（幂等，已有 shareId 时复用）。
     */
    @PostMapping("/conversations/{conversationId}/share")
    public ApiResponse<ConversationShareResponse> createShare(@PathVariable("conversationId") String conversationId) {
        log.info("创建会话分享请求，conversationId={}", conversationId);
        return ApiResponse.success(chatConversationExtraService.createShare(conversationId));
    }

    /**
     * 取消会话分享。
     */
    @DeleteMapping("/conversations/{conversationId}/share")
    public ApiResponse<Boolean> removeShare(@PathVariable("conversationId") String conversationId) {
        log.info("取消会话分享请求，conversationId={}", conversationId);
        return ApiResponse.success(chatConversationExtraService.removeShare(conversationId));
    }

    /**
     * 查询公开分享只读数据（无需登录）。
     */
    @GetMapping("/share/{shareId}")
    public ApiResponse<ChatShareResponse> share(@PathVariable("shareId") String shareId) {
        log.info("查询公开会话分享，shareId={}", shareId);
        return ApiResponse.success(chatConversationExtraService.getShare(shareId));
    }

    /**
     * 导出会话为 Markdown 附件。
     */
    @GetMapping("/conversations/{conversationId}/export")
    public ResponseEntity<byte[]> export(@PathVariable("conversationId") String conversationId) {
        String markdown = chatConversationExtraService.exportMarkdown(conversationId);
        String shortId = conversationId.length() > EXPORT_FILENAME_ID_LENGTH
                ? conversationId.substring(0, EXPORT_FILENAME_ID_LENGTH)
                : conversationId;
        byte[] body = markdown.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(MARKDOWN_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"conversation-" + shortId + ".md\"")
                .body(body);
    }

    /**
     * 更新会话标题。
     */
    @PutMapping("/conversations/{conversationId}/title")
    public ApiResponse<Boolean> updateTitle(
            @PathVariable("conversationId") String conversationId,
            @Valid @RequestBody ConversationTitleRequest request
    ) {
        log.info("更新会话标题请求，conversationId={}", conversationId);
        return ApiResponse.success(chatConversationExtraService.updateTitle(conversationId, request.title()));
    }

    /**
     * 基于首轮对话生成会话标题（模型失败时降级为截断标题）。
     */
    @PostMapping("/conversations/{conversationId}/title/generate")
    public ApiResponse<ConversationTitleResponse> generateTitle(@PathVariable("conversationId") String conversationId) {
        log.info("生成会话标题请求，conversationId={}", conversationId);
        return ApiResponse.success(chatConversationExtraService.generateTitle(conversationId));
    }

    /**
     * 校验会话访问权限。
     */
    private AigcConversation assertConversationAccess(String conversationId) {
        if (!hasText(conversationId)) {
            throw new BizException(CoreErrorCode.INVALID_CHAT_REQUEST);
        }
        AigcConversation conversation = aigcConversationService.getById(conversationId);
        if (conversation == null) {
            throw new BizException(CoreErrorCode.CONVERSATION_NOT_FOUND);
        }
        String userId = AuthUtil.getUserId();
        if (hasText(userId) && hasText(conversation.getCreator()) && !userId.equals(conversation.getCreator())) {
            throw new BizException(CoreErrorCode.CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    /**
     * 解析智能体 ID。
     */
    private String resolveAgentId(ChatCompletionRequest request) {
        if (request == null || !hasText(request.getAgentId())) {
            throw new BizException(CoreErrorCode.INVALID_CHAT_REQUEST);
        }
        return request.getAgentId();
    }

    /**
     * 获取最后一条用户消息内容。
     */
    private String resolveLatestUserMessage(ChatCompletionRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new BizException(CoreErrorCode.INVALID_CHAT_REQUEST);
        }
        return request.getMessages().stream()
                .filter(message -> ChatRoleEnum.USER.code().equalsIgnoreCase(message.getRole()))
                .reduce((first, second) -> second)
                .map(ChatCompletionMessage::getContent)
                .filter(this::hasText)
                .orElseThrow(() -> new BizException(CoreErrorCode.INVALID_CHAT_REQUEST));
    }

    /**
     * 判断文本是否有值。
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
