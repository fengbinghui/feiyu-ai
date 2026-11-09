package cn.langchat.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员重置用户密码请求对象。
 *
 * @param newPassword 新密码（6-64 位）
 * @author LangChat Team
 * @since 2026/11/9
 */
public record ResetPasswordRequest(
        @NotBlank(message = "newPassword 不能为空")
        @Size(min = 6, max = 64, message = "newPassword 长度需为 6-64 位")
        String newPassword
) {
}
