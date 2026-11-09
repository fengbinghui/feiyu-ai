package cn.langchat.aigc.api.model.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 技能更新请求对象。
 *
 * <p>字段与 {@code cn.langchat.aigc.biz.entity.AigcSkill} 的业务字段一一对应
 * （不含 id 与 creator/updater/createTime/updateTime 审计字段）。</p>
 *
 * <p>技能的创建走技能包上传接口（multipart），本 DTO 只服务于 JSON 更新接口：
 * 前端既有调用为局部更新（仅提交 tags / enabled，如启用开关），
 * 因此这里不设置 {@code @NotBlank} 必填约束，仅按列宽限制长度，
 * 保持原有局部更新行为不变。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Data
public class SkillSaveRequest {

    /** 技能标识（取自 SKILL.md frontmatter name）。 */
    @Size(max = 100, message = "技能标识长度不能超过100位")
    private String name;

    /** 技能标题。 */
    @Size(max = 255, message = "技能标题长度不能超过255位")
    private String title;

    /** 技能描述。 */
    @Size(max = 1000, message = "技能描述长度不能超过1000位")
    private String description;

    /** 技能版本。 */
    @Size(max = 50, message = "技能版本长度不能超过50位")
    private String version;

    /** 入口文档相对路径。 */
    @Size(max = 255, message = "入口文档路径长度不能超过255位")
    private String entryFile;

    /** 开源协议。 */
    @Size(max = 100, message = "开源协议长度不能超过100位")
    private String license;

    /** 标签，逗号分隔。 */
    @Size(max = 500, message = "技能标签长度不能超过500位")
    private String tags;

    /** 是否启用。 */
    private Boolean enabled;

    /** 原始技能包大小（字节）。 */
    private Long packageSize;

    /** 包内文件数量。 */
    private Integer fileCount;

    /** 原始包在 OSS 中的 objectKey。 */
    @Size(max = 500, message = "OSS objectKey 长度不能超过500位")
    private String ossObjectKey;

    /** 原始包文件名。 */
    @Size(max = 255, message = "原始包文件名长度不能超过255位")
    private String ossFilename;

    /** 本地工作区相对路径。 */
    @Size(max = 500, message = "本地工作区路径长度不能超过500位")
    private String localPath;
}
