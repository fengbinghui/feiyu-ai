package cn.langchat.auth.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 自助注册请求对象。
 *
 * @param username 用户名（仅字母、数字、下划线，3-32 位）
 * @param password 密码（6-64 位）
 * @param realName 真实姓名（可选）
 * @param email 邮箱（可选）
 * @param phone 手机号（可选）
 * @author LangChat Team
 * @since 2026/11/9
 */
public record RegisterRequest(
        @NotBlank(message = "username 不能为空")
        @Size(min = 3, max = 32, message = "username 长度需为 3-32 位")
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "username 仅允许字母、数字与下划线")
        String username,

        @NotBlank(message = "password 不能为空")
        @Size(min = 6, max = 64, message = "password 长度需为 6-64 位")
        String password,

        @Size(max = 50, message = "realName 长度不能超过 50 位")
        String realName,

        @Email(message = "email 格式不正确")
        @Size(max = 100, message = "email 长度不能超过 100 位")
        String email,

        @Size(max = 20, message = "phone 长度不能超过 20 位")
        String phone
) {
}
