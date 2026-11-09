package cn.langchat.auth.model.response;

/**
 * 注册成功响应对象（不包含密码等敏感信息，也不自动登录）。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @author LangChat Team
 * @since 2026/11/9
 */
public record AuthRegisterResponse(
        String userId,
        String username
) {
}
