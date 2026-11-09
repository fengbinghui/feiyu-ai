package cn.langchat.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.langchat.auth.config.AuthProperties;
import cn.langchat.auth.domain.account.LocalUserAccount;
import cn.langchat.auth.domain.model.AuthUserProfile;
import cn.langchat.auth.entity.AigcMenu;
import cn.langchat.auth.entity.AigcRole;
import cn.langchat.auth.entity.AigcRoleMenu;
import cn.langchat.auth.entity.AigcUser;
import cn.langchat.auth.entity.AigcUserRole;
import cn.langchat.auth.infrastructure.LocalUserAccountRepository;
import cn.langchat.auth.model.request.AuthLoginRequest;
import cn.langchat.auth.model.request.ChangePasswordRequest;
import cn.langchat.auth.model.request.RegisterRequest;
import cn.langchat.auth.model.request.ResetPasswordRequest;
import cn.langchat.auth.model.request.UpdateProfileRequest;
import cn.langchat.auth.model.response.AuthCurrentUserResponse;
import cn.langchat.auth.model.response.AuthLoginResponse;
import cn.langchat.auth.model.response.AuthRegisterResponse;
import cn.langchat.auth.security.LoginAttemptService;
import cn.langchat.auth.service.AigcMenuService;
import cn.langchat.auth.service.AigcRoleMenuService;
import cn.langchat.auth.service.AigcRoleService;
import cn.langchat.auth.service.AigcUserRoleService;
import cn.langchat.auth.service.AigcUserService;
import cn.langchat.auth.service.AuthService;
import cn.langchat.common.auth.AuthUtil;
import cn.langchat.common.core.CommonErrorCode;
import cn.langchat.common.exception.BizException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证业务实现。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AigcUserService userService;
    private final AigcRoleService roleService;
    private final AigcMenuService menuService;
    private final AigcUserRoleService userRoleService;
    private final AigcRoleMenuService roleMenuService;
    private final LocalUserAccountRepository localUserAccountRepository;
    private final AuthProperties authProperties;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AuthLoginResponse login(AuthLoginRequest request) {
        String username = request.username();
        // 登录风控：命中锁定期直接返回 429，避免继续尝试猜测密码。
        loginAttemptService.checkAllowed(username);

        // 数据库用户优先：历史明文密码在登录成功后平滑升级为 BCrypt。
        AigcUser dbUser = userService.lambdaQuery()
                .eq(AigcUser::getUsername, username)
                .one();
        if (dbUser != null && passwordMatches(request.password(), dbUser.getPassword())) {
            upgradePasswordIfNeeded(dbUser, request.password());
            return doLogin(toProfile(dbUser));
        }

        // 数据库账户体系未初始化时的本地配置兜底账户，同样兼容 BCrypt 与历史明文。
        Optional<LocalUserAccount> localAccount = localUserAccountRepository.findByUsername(username)
                .filter(candidate -> passwordMatches(request.password(), candidate.password()));
        if (localAccount.isPresent()) {
            return doLogin(toProfile(localAccount.get()));
        }

        loginAttemptService.recordFailure(username);
        throw new BizException(CommonErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    /**
     * 登录成功后写入 Sa-Token 会话，后续接口统一走会话态获取当前用户。
     */
    private AuthLoginResponse doLogin(AuthUserProfile account) {
        loginAttemptService.recordSuccess(account.username());
        StpUtil.login(account.userId());
        log.info("用户登录成功，userId={}, username={}", account.userId(), account.username());
        return new AuthLoginResponse(
                account.userId(),
                account.username(),
                account.displayName(),
                account.tenantId(),
                StpUtil.getTokenName(),
                StpUtil.getTokenValue(),
                account.roles(),
                account.permissions()
        );
    }

    /**
     * 密码校验：BCrypt 哈希走 encoder，历史明文走常量时间比较（兼容旧数据）。
     */
    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isEmpty()) {
            return false;
        }
        if (isBcrypt(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return MessageDigest.isEqual(
                rawPassword.getBytes(StandardCharsets.UTF_8),
                storedPassword.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 历史明文密码平滑迁移：首次校验通过后立即替换为 BCrypt 哈希。
     */
    private void upgradePasswordIfNeeded(AigcUser user, String rawPassword) {
        if (isBcrypt(user.getPassword())) {
            return;
        }
        AigcUser patch = new AigcUser();
        patch.setId(user.getId());
        patch.setPassword(passwordEncoder.encode(rawPassword));
        userService.updateById(patch);
        log.info("检测到历史明文密码，已升级为 BCrypt，userId={}, username={}", user.getId(), user.getUsername());
    }

    private boolean isBcrypt(String password) {
        return password != null && password.startsWith("$2");
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public AuthCurrentUserResponse currentUser() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED);
        }

        AuthUserProfile account = findByUserId(String.valueOf(loginId))
                .or(() -> localUserAccountRepository.findByUserId(String.valueOf(loginId)).map(this::toProfile))
                .orElseThrow(() -> new BizException(CommonErrorCode.UNAUTHORIZED));

        // 个人中心需要回填邮箱/手机号/头像等资料字段，数据库用户直接读取实体，
        // 本地兜底账户没有这些字段则返回 null。
        AigcUser dbUser = userService.getById(account.userId());
        String email = dbUser == null ? null : dbUser.getEmail();
        String phone = dbUser == null ? null : dbUser.getPhone();
        String avatar = dbUser == null ? null : dbUser.getAvatar();

        return new AuthCurrentUserResponse(
                account.userId(),
                account.username(),
                account.displayName(),
                account.tenantId(),
                account.roles(),
                account.permissions(),
                email,
                phone,
                avatar
        );
    }

    // ------------------------------------------------------------------
    // 阶段3a：账号体系（自助注册 / 修改密码 / 个人资料 / 管理员重置密码）
    // ------------------------------------------------------------------

    @Override
    public boolean registerEnabled() {
        return authProperties.isRegisterEnabled();
    }

    @Override
    public AuthRegisterResponse register(RegisterRequest request) {
        if (!authProperties.isRegisterEnabled()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "注册功能未开启");
        }
        String username = request.username().trim();
        // 安全约束：注册入口不允许创建超级管理员账号。
        if (isSuperAdmin(username)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "该用户名不可用");
        }
        Long exists = userService.lambdaQuery().eq(AigcUser::getUsername, username).count();
        if (exists != null && exists > 0) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "用户名已存在");
        }

        AigcUser user = new AigcUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRealName(StringUtils.hasText(request.realName()) ? request.realName().trim() : username);
        user.setEmail(trimToNull(request.email()));
        user.setPhone(trimToNull(request.phone()));
        user.setStatus(1);
        userService.save(user);
        bindDefaultRole(user.getId());
        log.info("用户注册成功，userId={}, username={}", user.getId(), username);
        return new AuthRegisterResponse(user.getId(), username);
    }

    /**
     * 绑定注册用户默认角色；角色不存在时仅告警，用户仍创建成功（由管理员后续补授权）。
     */
    private void bindDefaultRole(String userId) {
        String roleCode = authProperties.getDefaultRoleCode();
        if (!StringUtils.hasText(roleCode)) {
            log.warn("未配置注册用户默认角色编码，跳过角色绑定，userId={}", userId);
            return;
        }
        AigcRole role = roleService.lambdaQuery().eq(AigcRole::getCode, roleCode.trim()).one();
        if (role == null) {
            log.warn("注册用户默认角色不存在，跳过角色绑定，userId={}, roleCode={}", userId, roleCode);
            return;
        }
        AigcUserRole userRole = new AigcUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRoleService.save(userRole);
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        AigcUser user = requireCurrentDbUser();
        if (!passwordMatches(request.oldPassword(), user.getPassword())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "旧密码不正确");
        }
        if (request.oldPassword().equals(request.newPassword())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "新密码不能与旧密码相同");
        }
        updatePassword(user.getId(), request.newPassword());
        // 改密成功后清除登录失败计数与锁定状态，避免历史失败记录影响新密码登录。
        loginAttemptService.clear(user.getUsername());
        log.info("用户修改密码成功，userId={}, username={}", user.getId(), user.getUsername());
    }

    @Override
    public AuthCurrentUserResponse updateProfile(UpdateProfileRequest request) {
        AigcUser user = requireCurrentDbUser();
        AigcUser patch = new AigcUser();
        patch.setId(user.getId());
        if (request.realName() != null) {
            patch.setRealName(StringUtils.hasText(request.realName()) ? request.realName().trim() : null);
        }
        if (request.email() != null) {
            patch.setEmail(trimToNull(request.email()));
        }
        if (request.phone() != null) {
            patch.setPhone(trimToNull(request.phone()));
        }
        if (request.avatar() != null) {
            patch.setAvatar(StringUtils.hasText(request.avatar()) ? request.avatar().trim() : null);
        }
        userService.updateById(patch);
        log.info("用户更新个人资料，userId={}, username={}", user.getId(), user.getUsername());
        return currentUser();
    }

    @Override
    public void resetPassword(String userId, ResetPasswordRequest request) {
        AigcUser user = userService.getById(userId);
        if (user == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "用户不存在");
        }
        updatePassword(user.getId(), request.newPassword());
        // 管理员重置密码后清除登录失败锁定，确保用户可立即用新密码登录。
        loginAttemptService.clear(user.getUsername());
        log.info("管理员重置用户密码成功，userId={}, username={}", user.getId(), user.getUsername());
    }

    /**
     * 获取当前登录用户的数据库账户（本地配置兜底账户不支持改密/改资料）。
     */
    private AigcUser requireCurrentDbUser() {
        String userId = AuthUtil.getUserId();
        AigcUser user = userId == null ? null : userService.getById(userId);
        if (user == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "当前账号不支持该操作");
        }
        return user;
    }

    private void updatePassword(String userId, String rawPassword) {
        AigcUser patch = new AigcUser();
        patch.setId(userId);
        patch.setPassword(passwordEncoder.encode(rawPassword));
        userService.updateById(patch);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 查询用户画像。
     *
     * 优先走数据库实体，再兜底读取本地配置账户。
     */
    @Override
    public Optional<AuthUserProfile> findByUserId(String userId) {        return Optional.ofNullable(userService.getById(userId))
                .map(this::toProfile)
                .or(() -> localUserAccountRepository.findByUserId(userId).map(this::toProfile));
    }

    private AuthUserProfile toProfile(AigcUser user) {
        boolean superAdmin = isSuperAdmin(user.getUsername());

        List<String> roleIds = userRoleService.lambdaQuery()
                .eq(AigcUserRole::getUserId, user.getId())
                .list()
                .stream()
                .map(AigcUserRole::getRoleId)
                .distinct()
                .toList();

        List<String> roles = roleIds.isEmpty()
                ? List.of()
                : roleService.listByIds(roleIds).stream()
                .map(AigcRole::getCode)
                .filter(code -> code != null && !code.isBlank())
                .sorted()
                .toList();
        if (superAdmin && !roles.contains("SUPER_ADMIN")) {
            List<String> superAdminRoles = new ArrayList<>(roles);
            superAdminRoles.add("SUPER_ADMIN");
            roles = superAdminRoles.stream().distinct().sorted().toList();
        }

        List<String> menuIds = roleIds.isEmpty()
                ? List.of()
                : roleMenuService.lambdaQuery()
                .in(AigcRoleMenu::getRoleId, roleIds)
                .list()
                .stream()
                .map(AigcRoleMenu::getMenuId)
                .distinct()
                .toList();

        List<String> permissions = superAdmin
                ? listAllPermissions()
                : menuIds.isEmpty()
                ? List.of()
                : menuService.listByIds(menuIds).stream()
                .filter(menu -> menu.getPerms() != null && !menu.getPerms().isBlank())
                .sorted(Comparator.comparing(AigcMenu::getPerms))
                .map(AigcMenu::getPerms)
                .toList();

        return new AuthUserProfile(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getRealName() == null || user.getRealName().isBlank() ? user.getUsername() : user.getRealName(),
                null,
                roles,
                permissions
        );
    }

    private AuthUserProfile toProfile(LocalUserAccount account) {
        boolean superAdmin = isSuperAdmin(account.username());
        List<String> roles = account.roles();
        if (superAdmin && !roles.contains("SUPER_ADMIN")) {
            roles = new ArrayList<>(roles);
            roles.add("SUPER_ADMIN");
            roles = roles.stream().distinct().sorted().toList();
        }
        return new AuthUserProfile(
                account.userId(),
                account.username(),
                account.password(),
                account.displayName(),
                account.tenantId(),
                roles,
                superAdmin ? listAllPermissions() : account.permissions()
        );
    }

    private List<String> listAllPermissions() {
        return menuService.lambdaQuery()
                .orderByAsc(AigcMenu::getPerms)
                .list()
                .stream()
                .map(AigcMenu::getPerms)
                .filter(perms -> perms != null && !perms.isBlank())
                .distinct()
                .toList();
    }

    private boolean isSuperAdmin(String username) {
        String superAdminUsername = authProperties.getSuperAdminUsername();
        return username != null
                && superAdminUsername != null
                && !superAdminUsername.isBlank()
                && superAdminUsername.equals(username);
    }
}
