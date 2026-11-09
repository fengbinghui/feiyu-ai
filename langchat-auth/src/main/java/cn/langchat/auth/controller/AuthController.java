package cn.langchat.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.langchat.auth.model.request.AuthLoginRequest;
import cn.langchat.auth.model.request.ChangePasswordRequest;
import cn.langchat.auth.model.request.RegisterRequest;
import cn.langchat.auth.model.request.UpdateProfileRequest;
import cn.langchat.auth.model.response.AuthCurrentUserResponse;
import cn.langchat.auth.model.response.AuthLoginResponse;
import cn.langchat.auth.model.response.AuthRegisterResponse;
import cn.langchat.auth.model.response.AuthRouteRecordResponse;
import cn.langchat.auth.service.AigcMenuService;
import cn.langchat.auth.service.AuthService;
import cn.langchat.common.auth.AuthUtil;
import cn.langchat.common.core.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证控制器。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final AigcMenuService aigcMenuService;

    @PostMapping("/login")
    public ApiResponse<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 查询是否开放自助注册（公开接口，注册页读取开关）。
     */
    @GetMapping("/register-enabled")
    public ApiResponse<Boolean> registerEnabled() {
        return ApiResponse.success(authService.registerEnabled());
    }

    /**
     * 自助注册（公开接口，成功后不自动登录）。
     */
    @PostMapping("/register")
    public ApiResponse<AuthRegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    /**
     * 当前登录用户修改密码。
     */
    @PostMapping("/change-password")
    @SaCheckLogin
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.success(null);
    }

    /**
     * 当前登录用户更新个人资料。
     */
    @PutMapping("/profile")
    @SaCheckLogin
    public ApiResponse<AuthCurrentUserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(authService.updateProfile(request));
    }

    @PostMapping("/logout")
    @SaCheckLogin
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    @SaCheckLogin
    public ApiResponse<AuthCurrentUserResponse> me() {
        return ApiResponse.success(authService.currentUser());
    }

    /**
     * 查询当前登录用户的动态路由。
     */
    @GetMapping("/access-routes")
    @SaCheckLogin
    public ApiResponse<List<AuthRouteRecordResponse>> accessRoutes() {
        String userId = AuthUtil.getUserId();
        return ApiResponse.success(aigcMenuService.listCurrentUserRoutes(userId));
    }
}
