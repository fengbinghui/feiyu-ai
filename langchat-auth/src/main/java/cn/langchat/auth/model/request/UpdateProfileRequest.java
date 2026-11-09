package cn.langchat.auth.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 更新个人资料请求对象（仅允许更新非敏感资料字段）。
 *
 * @param realName 真实姓名（可选，≤50）
 * @param email 邮箱（可选）
 * @param phone 手机号（可选，≤20）
 * @param avatar 头像地址（可选，≤500）
 * @author LangChat Team
 * @since 2026/11/9
 */
public record UpdateProfileRequest(
        @Size(max = 50, message = "realName 长度不能超过 50 位")
        String realName,

        @Email(message = "email 格式不正确")
        @Size(max = 100, message = "email 长度不能超过 100 位")
        String email,

        @Size(max = 20, message = "phone 长度不能超过 20 位")
        String phone,

        @Size(max = 500, message = "avatar 长度不能超过 500 位")
        String avatar
) {
}
