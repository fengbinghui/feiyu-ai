package cn.langchat.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色新增/更新请求对象。
 *
 * <p>字段与 {@link cn.langchat.auth.entity.AigcRole} 的业务字段一一对应
 * （不含 id 与审计字段），前端现有 JSON 请求体无需改动。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Data
public class AigcRoleSaveRequest {

    /** 角色名称。 */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称长度不能超过50位")
    private String name;

    /** 角色编码。 */
    @Size(max = 50, message = "角色编码长度不能超过50位")
    private String code;

    /** 角色描述。 */
    @Size(max = 100, message = "角色描述长度不能超过100位")
    private String description;
}
