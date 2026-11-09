package cn.langchat.auth.model.response;

import java.util.List;

/**
 * 当前登录用户响应对象。
 *
 * @param userId 用户ID
 * @param username 用户名
 * @param displayName 展示名称
 * @param tenantId 租户ID
 * @param roles 角色编码列表
 * @param permissions 权限标识列表
 * @param email 邮箱
 * @param phone 手机号
 * @param avatar 头像地址
 * @author LangChat Team
 * @since 2026/3/24
 */
public record AuthCurrentUserResponse(
        String userId,
        String username,
        String displayName,
        String tenantId,
        List<String> roles,
        List<String> permissions,
        String email,
        String phone,
        String avatar
) {
}
