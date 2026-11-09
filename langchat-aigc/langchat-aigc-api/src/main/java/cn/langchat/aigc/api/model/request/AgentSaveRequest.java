package cn.langchat.aigc.api.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 新增/更新请求对象。
 *
 * <p>字段与 {@code cn.langchat.aigc.biz.entity.AigcAgent} 的业务字段一一对应
 * （不含 id 与 creator/updater/createTime/updateTime 审计字段），
 * 前端现有 JSON 请求体无需改动。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Data
public class AgentSaveRequest {

    /** Agent 名称。 */
    @NotBlank(message = "Agent 名称不能为空")
    @Size(max = 255, message = "Agent 名称长度不能超过255位")
    private String agentName;

    /** Agent 描述。 */
    @Size(max = 500, message = "Agent 描述长度不能超过500位")
    private String description;

    /** Agent 状态（如 DRAFT / PUBLISHED / DISABLED）。 */
    @Size(max = 20, message = "Agent 状态长度不能超过20位")
    private String status;

    /** 系统提示词（longtext 列，不限制长度）。 */
    private String systemPrompt;

    /** 推理模型 ID。 */
    @Size(max = 50, message = "推理模型ID长度不能超过50位")
    private String reasoningModelId;

    /** 关联知识库 ID 列表（text 列，JSON 数组字符串）。 */
    private String knowledgeIds;

    /** 关联技能 ID 列表（text 列，JSON 数组字符串）。 */
    private String skillIds;

    /** 关联 MCP 服务 ID 列表（text 列，JSON 数组字符串）。 */
    private String mcpIds;

    /** 模型配置 JSON（text 列）。 */
    private String modelConfigJson;

    /** Agent 头像。 */
    @Size(max = 500, message = "Agent 头像地址长度不能超过500位")
    private String avatar;

    /** 扩展元数据 JSON（text 列）。 */
    private String metaJson;
}
