package cn.langchat.server.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 授权配置。
 *
 * <p>只有注册 {@link SaInterceptor} 后，Controller 上的 {@code @SaCheckLogin} /
 * {@code @SaCheckPermission} 注解才会生效。本配置统一要求：除登录与自助注册相关接口外，
 * 所有 {@code /api/**} 接口必须登录后才能访问；具体接口的权限码由 Controller 类上的
 * {@code @SaCheckPermission} 注解校验（超级管理员通配 {@code *}）。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    /** 登录接口放行（登录时尚无凭证）。 */
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    /** 自助注册接口放行（注册时尚无凭证）。 */
    private static final String REGISTER_PATH = "/api/v1/auth/register";

    /** 注册开关查询接口放行（注册页需要先读取开关状态）。 */
    private static final String REGISTER_ENABLED_PATH = "/api/v1/auth/register-enabled";

    /** 会话公开分享查看接口放行（分享页无需登录即可查看）。 */
    private static final String CHAT_SHARE_VIEW_PATH = "/api/v1/chat/share/**";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
                    // CORS 预检请求不携带业务凭证，交给 CORS 过滤器处理，跳过登录校验。
                    String method = SaHolder.getRequest().getMethod();
                    if ("OPTIONS".equalsIgnoreCase(method)) {
                        return;
                    }
                    // 除登录 / 注册 / 会话公开分享等公开接口外，所有 /api/** 接口一律要求登录。
                    SaRouter.match("/api/**")
                            .notMatch(LOGIN_PATH, REGISTER_PATH, REGISTER_ENABLED_PATH, CHAT_SHARE_VIEW_PATH)
                            .check(route -> StpUtil.checkLogin());
                })
                // 开启注解鉴权，使 @SaCheckLogin / @SaCheckPermission 生效。
                .isAnnotation(true))
                .addPathPatterns("/**")
                // OpenAI 兼容接口使用独立的 API Key 鉴权；actuator / 错误页无需登录态。
                .excludePathPatterns("/v1/**", "/actuator/**", "/error");
    }
}
