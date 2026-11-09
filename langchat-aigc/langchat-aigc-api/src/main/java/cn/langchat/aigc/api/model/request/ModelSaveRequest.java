package cn.langchat.aigc.api.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

/**
 * 模型新增/更新请求对象。
 *
 * <p>字段与 {@code cn.langchat.aigc.biz.entity.AigcModel} 的业务字段一一对应
 * （不含 id 与 creator/updater/createTime/updateTime 审计字段），
 * 前端现有 JSON 请求体无需改动。{@code configJson} 为 JSON 列，不限制长度。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Data
public class ModelSaveRequest {

    /** 模型类型（如 TEXT2TEXT / EMBEDDINGS）。 */
    @NotBlank(message = "模型类型不能为空")
    @Size(max = 100, message = "模型类型长度不能超过100位")
    private String type;

    /** 模型名称。 */
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称长度不能超过100位")
    private String model;

    /** 模型供应商。 */
    @NotBlank(message = "模型供应商不能为空")
    @Size(max = 100, message = "模型供应商长度不能超过100位")
    private String provider;

    /** 模型别名。 */
    @NotBlank(message = "模型别名不能为空")
    @Size(max = 100, message = "模型别名长度不能超过100位")
    private String name;

    /** 最大输出 Token。 */
    private Integer maxToken;

    /** 温度参数。 */
    private Double temperature;

    /** TopP 参数。 */
    private Double topP;

    /** API Key。 */
    @Size(max = 255, message = "API Key 长度不能超过255位")
    private String apiKey;

    /** 超时时间。 */
    private Integer timeout;

    /** 模型基础地址。 */
    @Size(max = 100, message = "模型基础地址长度不能超过100位")
    private String baseUrl;

    /** 模型类型专属配置 JSON（如向量模型的 dimension）。 */
    private Map<String, Object> configJson;
}
