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
import cn.langchat.auth.model.response.AuthCurrentUserResponse;
import cn.langchat.auth.model.response.AuthLoginResponse;
import cn.langchat.auth.security.LoginAttemptService;
import cn.langchat.auth.service.AigcMenuService;
import cn.langchat.auth.service.AigcRoleMenuService;
import cn.langchat.auth.service.AigcRoleService;
import cn.langchat.auth.service.AigcUserRoleService;
import cn.langchat.auth.service.AigcUserService;
import cn.langchat.auth.service.AuthService;
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

        return new AuthCurrentUserResponse(
                account.userId(),
                account.username(),
                account.displayName(),
                account.tenantId(),
                account.roles(),
                account.permissions()
        );
    }

    /**
     * 查询用户画像。
     *
     * 优先走数据库实体，再兜底读取本地配置账户。
     */
    @Override
    public Optional<AuthUserProfile> findByUserId(String userId) {
        return Optional.ofNullable(userService.getById(userId))
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
