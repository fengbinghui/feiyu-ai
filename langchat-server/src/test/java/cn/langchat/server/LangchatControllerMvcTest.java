package cn.langchat.server;

import cn.langchat.aigc.api.controller.AigcLogController;
import cn.langchat.aigc.biz.entity.AigcLog;
import cn.langchat.aigc.biz.service.AigcConversationService;
import cn.langchat.aigc.biz.service.AigcLogService;
import cn.langchat.aigc.biz.service.AigcMessageService;
import cn.langchat.core.chat.controller.ChatController;
import cn.langchat.core.chat.service.AgentChatRuntimeService;
import cn.langchat.core.chat.service.ChatConversationExtraService;
import cn.langchat.starter.web.GlobalExceptionHandler;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 控制器 MVC 集成测试（MockMvc standaloneSetup + Mockito，不启动 Spring 容器、不连数据库）。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
class LangchatControllerMvcTest {

    @Test
    void auditLogListReturnsPagedRecords() throws Exception {
        AigcLogService aigcLogService = mock(AigcLogService.class);
        AigcLog first = new AigcLog();
        first.setId("log-1");
        first.setType("LOGIN");
        AigcLog second = new AigcLog();
        second.setId("log-2");
        second.setType("CHAT");
        when(aigcLogService.count(any())).thenReturn(2L);
        when(aigcLogService.list(org.mockito.ArgumentMatchers.<Wrapper<AigcLog>>any()))
                .thenReturn(List.of(first, second));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AigcLogController(aigcLogService))
                .build();

        mockMvc.perform(get("/api/v1/aigc/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.pageNo").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20));
    }

    @Test
    void auditLogListAppliesPageParameters() throws Exception {
        AigcLogService aigcLogService = mock(AigcLogService.class);
        when(aigcLogService.count(any())).thenReturn(0L);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AigcLogController(aigcLogService))
                .build();

        mockMvc.perform(get("/api/v1/aigc/logs")
                        .param("pageNo", "2")
                        .param("pageSize", "999")
                        .param("type", "LOGIN")
                        .param("keyword", "登录"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(200));
    }

    @Test
    void blankTitleReturnsBadRequest() throws Exception {
        ChatController chatController = newChatController(mock(ChatConversationExtraService.class));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(chatController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(put("/api/v1/chat/conversations/conv-1/title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void validTitleReturnsOkAndCallsService() throws Exception {
        ChatConversationExtraService extraService = mock(ChatConversationExtraService.class);
        when(extraService.updateTitle(eq("conv-1"), eq("正常标题"))).thenReturn(true);
        ChatController chatController = newChatController(extraService);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(chatController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(put("/api/v1/chat/conversations/conv-1/title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"正常标题\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        verify(extraService).updateTitle("conv-1", "正常标题");
    }

    private ChatController newChatController(ChatConversationExtraService extraService) {
        return new ChatController(
                mock(AigcConversationService.class),
                mock(AigcMessageService.class),
                mock(AgentChatRuntimeService.class),
                extraService
        );
    }
}
