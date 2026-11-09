package cn.langchat.aigc.api.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * MCP 新增/更新请求对象。
 *
 * <p>字段与 {@code cn.langchat.aigc.biz.entity.AigcMcp} 的业务字段一一对应
 * （不含 id 与 creator/updater/createTime/updateTime 审计字段），
 * 前端现有 JSON 请求体无需改动。{@code mcpJson} / {@code headers} 为 text 列，不限制长度。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Data
public class McpSaveRequest {

    /** UUID。 */
    @Size(max = 50, message = "UUID 长度不能超过50位")
    private String uuid;

    /** 服务名称。 */
    @NotBlank(message = "MCP 服务名称不能为空")
    @Size(max = 255, message = "MCP 服务名称长度不能超过255位")
    private String name;

    /** MCP 配置 JSON（text 列，不限制长度）。 */
    private String mcpJson;

    /** 封面地址。 */
    @Size(max = 255, message = "封面地址长度不能超过255位")
    private String coverUrl;

    /** 标签。 */
    @Size(max = 500, message = "标签长度不能超过500位")
    private String tags;

    /** 是否已授权。 */
    private Boolean authorized;

    /** 协议类型（如 SSE / HTTP / STDIO / DOCKER）。 */
    @Size(max = 50, message = "协议类型长度不能超过50位")
    private String transport;

    /** 请求地址。 */
    @Size(max = 500, message = "请求地址长度不能超过500位")
    private String sseUrl;

    /** 请求头（text 列，不限制长度）。 */
    private String headers;

    /** Docker 镜像。 */
    @Size(max = 100, message = "Docker 镜像长度不能超过100位")
    private String dockerImage;

    /** Docker Host。 */
    @Size(max = 100, message = "Docker Host 长度不能超过100位")
    private String dockerHost;

    /** 站点地址。 */
    @Size(max = 250, message = "站点地址长度不能超过250位")
    private String siteUrl;

    /** 超时时间。 */
    private Integer timeout;

    /** 描述信息。 */
    @Size(max = 500, message = "描述信息长度不能超过500位")
    private String description;
}
