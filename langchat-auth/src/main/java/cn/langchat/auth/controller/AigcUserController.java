package cn.langchat.auth.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.langchat.auth.entity.AigcUser;
import cn.langchat.auth.service.AigcUserService;
import cn.langchat.common.core.ApiResponse;
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
    public ApiResponse<Boolean> create(@RequestBody AigcUser user) {
        log.info("新增用户，username={}", user.getUsername());
        normalizePassword(user);
        return ApiResponse.success(aigcUserService.save(user));
    }

    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable("id") String id, @RequestBody AigcUser user) {
        user.setId(id);
        log.info("更新用户，id={}", id);
        // 密码为空/未传时保留原密码；非空密码统一 BCrypt 入库。
        normalizePassword(user);
        return ApiResponse.success(aigcUserService.updateById(user));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> remove(@PathVariable("id") String id) {
        log.info("删除用户，id={}", id);
        return ApiResponse.success(aigcUserService.removeById(id));
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
