package cn.langchat.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户新增/更新请求对象。
 *
 * <p>字段与 {@link cn.langchat.auth.entity.AigcUser} 的业务字段一一对应
 * （不含 id 与审计字段），前端现有 JSON 请求体无需改动。
 * 更新时 password 为空表示保留原密码。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Data
public class AigcUserSaveRequest {

    /** 用户名。 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50位")
    private String username;

    /** 登录密码（为空表示不设置/不修改）。 */
    @Size(max = 100, message = "密码长度不能超过100位")
    private String password;

    /** 真实姓名。 */
    @Size(max = 255, message = "真实姓名长度不能超过255位")
    private String realName;

    /** 性别。 */
    @Size(max = 10, message = "性别长度不能超过10位")
    private String sex;

    /** 手机号。 */
    @Size(max = 20, message = "手机号长度不能超过20位")
    private String phone;

    /** 邮箱。 */
    @Size(max = 100, message = "邮箱长度不能超过100位")
    private String email;

    /** 头像（mediumtext 列，不限制长度）。 */
    private String avatar;

    /** 状态：0 锁定 1 有效。 */
    private Integer status;

    /** 部门 ID。 */
    @Size(max = 50, message = "部门ID长度不能超过50位")
    private String deptId;
}
