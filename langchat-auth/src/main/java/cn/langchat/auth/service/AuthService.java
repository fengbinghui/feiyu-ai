package cn.langchat.auth.service;

import cn.langchat.auth.domain.model.AuthUserProfile;
import cn.langchat.auth.model.request.AuthLoginRequest;
import cn.langchat.auth.model.request.ChangePasswordRequest;
import cn.langchat.auth.model.request.RegisterRequest;
import cn.langchat.auth.model.request.ResetPasswordRequest;
import cn.langchat.auth.model.request.UpdateProfileRequest;
import cn.langchat.auth.model.response.AuthCurrentUserResponse;
import cn.langchat.auth.model.response.AuthLoginResponse;
import cn.langchat.auth.model.response.AuthRegisterResponse;
import java.util.Optional;

/**
 * 认证 Service 接口。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
public interface AuthService {

    AuthLoginResponse login(AuthLoginRequest request);

    void logout();

    AuthCurrentUserResponse currentUser();

    Optional<AuthUserProfile> findByUserId(String userId);

    /**
     * 是否开放自助注册。
     */
    boolean registerEnabled();

    /**
     * 自助注册（不自动登录）。
     */
    AuthRegisterResponse register(RegisterRequest request);

    /**
     * 当前登录用户修改密码。
     */
    void changePassword(ChangePasswordRequest request);

    /**
     * 更新当前登录用户的个人资料。
     */
    AuthCurrentUserResponse updateProfile(UpdateProfileRequest request);

    /**
     * 管理员重置指定用户密码。
     */
    void resetPassword(String userId, ResetPasswordRequest request);
}
