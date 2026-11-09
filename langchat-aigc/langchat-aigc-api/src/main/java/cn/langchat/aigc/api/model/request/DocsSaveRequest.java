package cn.langchat.aigc.api.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文档新增/更新请求对象。
 *
 * <p>字段与 {@code cn.langchat.aigc.biz.entity.AigcDocs} 的业务字段一一对应
 * （不含 id 与 creator/updater/createTime/updateTime 审计字段），
 * 前端现有 JSON 请求体无需改动。</p>
 *
 * <p>注意：上传接口 {@code POST /api/v1/aigc/docs/upload} 仍使用 multipart 参数，
 * 不走该请求对象。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Data
public class DocsSaveRequest {

    /** 文件资源 ID。 */
    @Size(max = 50, message = "文件资源ID长度不能超过50位")
    private String ossId;

    /** 知识库 ID。 */
    @NotBlank(message = "知识库ID不能为空")
    @Size(max = 50, message = "知识库ID长度不能超过50位")
    private String knowledgeId;

    /** 父节点 ID。 */
    @Size(max = 50, message = "父节点ID长度不能超过50位")
    private String parentId;

    /** 是否启用。 */
    private Boolean enabled;

    /** 索引状态。 */
    private Integer indexingStatus;

    /** 向量化状态。 */
    @Size(max = 20, message = "向量化状态长度不能超过20位")
    private String embedStatus;

    /** 向量化错误信息（text 列，不限制长度）。 */
    private String embedError;

    /** 向量化开始时间（毫秒时间戳）。 */
    private Long embedStartTime;

    /** 向量化结束时间（毫秒时间戳）。 */
    private Long embedEndTime;

    /** 导入配置（text 列，不限制长度）。 */
    private String ingestionConfig;

    /** 文档类型。 */
    @Size(max = 50, message = "文档类型长度不能超过50位")
    private String type;

    /** 文档名称。 */
    @NotBlank(message = "文档名称不能为空")
    @Size(max = 255, message = "文档名称长度不能超过255位")
    private String name;

    /** 文件后缀。 */
    @Size(max = 50, message = "文件后缀长度不能超过50位")
    private String ext;

    /** 文档地址。 */
    @Size(max = 500, message = "文档地址长度不能超过500位")
    private String url;

    /** PDF 地址。 */
    @Size(max = 500, message = "PDF地址长度不能超过500位")
    private String pdfUrl;

    /** 文档内容（text 列，不限制长度）。 */
    private String content;

    /** 文件大小（字节）。 */
    private Integer size;
}
