package cn.langchat.auth.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.langchat.auth.config.AuthProperties;
import cn.langchat.auth.entity.AigcUser;
import cn.langchat.auth.entity.AigcUserRole;
import cn.langchat.auth.model.request.ResetPasswordRequest;
import cn.langchat.auth.service.AigcUserRoleService;
import cn.langchat.auth.service.AigcUserService;
import cn.langchat.auth.service.AuthService;
import cn.langchat.common.core.ApiResponse;
import cn.langchat.common.core.CommonErrorCode;
import cn.langchat.common.core.audit.AuditLog;
import cn.langchat.common.core.audit.AuditLogType;
import cn.langchat.common.exception.BizException;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理控制器。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@RestController
@RequestMapping("/api/v1/auth/users")
@RequiredArgsConstructor
@Slf4j
@SaCheckPermission("langchat:user:list")
public class AigcUserController {

    private final AigcUserService aigcUserService;
    private final AigcUserRoleService aigcUserRoleService;
    private final AuthService authService;
    private final AuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ApiResponse<List<AigcUser>> list() {
        List<AigcUser> users = aigcUserService.lambdaQuery().orderByDesc(AigcUser::getCreateTime).list();
        // 敏感字段脱敏：列表接口不返回密码（含 BCrypt 哈希）。
        users.forEach(this::erasePassword);
        return ApiResponse.success(users);
    }

    @GetMapping("/{id}")
    public ApiResponse<AigcUser> detail(@PathVariable("id") String id) {
        AigcUser user = aigcUserService.getById(id);
        erasePassword(user);
        return ApiResponse.success(user);
    }

    @PostMapping
    @AuditLog(type = AuditLogType.USER, action = "新增用户")
    public ApiResponse<Boolean> create(@RequestBody AigcUser user) {
        log.info("新增用户，username={}", user.getUsername());
        normalizePassword(user);
        return ApiResponse.success(aigcUserService.save(user));
    }

    @PutMapping("/{id}")
    @AuditLog(type = AuditLogType.USER, action = "更新用户")
    public ApiResponse<Boolean> update(@PathVariable("id") String id, @RequestBody AigcUser user) {
        user.setId(id);
        log.info("更新用户，id={}", id);
        // 密码为空/未传时保留原密码；非空密码统一 BCrypt 入库。
        normalizePassword(user);
        return ApiResponse.success(aigcUserService.updateById(user));
    }

    @DeleteMapping("/{id}")
    @AuditLog(type = AuditLogType.USER, action = "删除用户")
    public ApiResponse<Boolean> remove(@PathVariable("id") String id) {
        log.info("删除用户，id={}", id);
        AigcUser user = aigcUserService.getById(id);
        if (user == null) {
            return ApiResponse.success(Boolean.FALSE);
        }
        // 安全约束：超级管理员账号不允许删除，避免误操作导致平台失去管理员。
        String superAdminUsername = authProperties.getSuperAdminUsername();
        if (StringUtils.hasText(superAdminUsername) && superAdminUsername.equals(user.getUsername())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "不能删除超级管理员账号");
        }
        // 级联清理用户角色关联，避免残留脏数据。
        aigcUserRoleService.lambdaUpdate()
                .eq(AigcUserRole::getUserId, id)
                .remove();
        boolean removed = aigcUserService.removeById(id);
        log.info("删除用户完成，id={}, username={}, removed={}", id, user.getUsername(), removed);
        return ApiResponse.success(removed);
    }

    /**
     * 管理员重置指定用户密码（类级权限 langchat:user:list）。
     */
    @PostMapping("/{id}/reset-password")
    @AuditLog(type = AuditLogType.USER, action = "重置用户密码")
    public ApiResponse<Void> resetPassword(
            @PathVariable("id") String id,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        log.info("管理员重置用户密码，id={}", id);
        authService.resetPassword(id, request);
        return ApiResponse.success(null);
    }

    /**
     * 密码处理：非空密码统一加密，空密码置为 null（更新时由 MyBatis-Plus 忽略 null 字段以保留原值）。
     */
    private void normalizePassword(AigcUser user) {
        if (StringUtils.hasText(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword().trim()));
        } else {
            user.setPassword(null);
        }
    }

    private void erasePassword(AigcUser user) {
        if (user != null) {
            user.setPassword(null);
        }
    }
}
