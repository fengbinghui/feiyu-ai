package cn.langchat.aigc.api.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库新增/更新请求对象。
 *
 * <p>字段与 {@code cn.langchat.aigc.biz.entity.AigcKnowledge} 的业务字段一一对应
 * （不含 id 与 creator/updater/createTime/updateTime 审计字段），
 * 前端现有 JSON 请求体无需改动。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Data
public class KnowledgeSaveRequest {

    /** 知识库名称。 */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 50, message = "知识库名称长度不能超过50位")
    private String name;

    /** 封面地址。 */
    @Size(max = 500, message = "知识库封面地址长度不能超过500位")
    private String coverUrl;

    /** 最大召回数量。 */
    private Integer maxResults;

    /** 最低分数阈值。 */
    private Double minScore;

    /** 向量库 ID。 */
    @Size(max = 50, message = "向量库ID长度不能超过50位")
    private String vectorStoreId;

    /** 向量模型 ID。 */
    @Size(max = 50, message = "向量模型ID长度不能超过50位")
    private String vectorModelId;

    /** 描述。 */
    @Size(max = 255, message = "知识库描述长度不能超过255位")
    private String description;

    /** 标签。 */
    @Size(max = 500, message = "知识库标签长度不能超过500位")
    private String tags;
}
