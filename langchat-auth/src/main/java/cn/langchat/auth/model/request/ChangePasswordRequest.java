package cn.langchat.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求对象。
 *
 * @param oldPassword 旧密码
 * @param newPassword 新密码（6-64 位）
 * @author LangChat Team
 * @since 2026/11/9
 */
public record ChangePasswordRequest(
        @NotBlank(message = "oldPassword 不能为空")
        String oldPassword,

        @NotBlank(message = "newPassword 不能为空")
        @Size(min = 6, max = 64, message = "newPassword 长度需为 6-64 位")
        String newPassword
) {
}
