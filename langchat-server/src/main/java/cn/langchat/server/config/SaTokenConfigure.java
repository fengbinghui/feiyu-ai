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
 * {@code @SaCheckPermission} 注解才会生效。本配置统一要求：除登录接口外，
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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
                    // CORS 预检请求不携带业务凭证，交给 CORS 过滤器处理，跳过登录校验。
                    String method = SaHolder.getRequest().getMethod();
                    if ("OPTIONS".equalsIgnoreCase(method)) {
                        return;
                    }
                    // 除登录接口外，所有 /api/** 接口一律要求登录。
                    SaRouter.match("/api/**")
                            .notMatch(LOGIN_PATH)
                            .check(route -> StpUtil.checkLogin());
                })
                // 开启注解鉴权，使 @SaCheckLogin / @SaCheckPermission 生效。
                .isAnnotation(true))
                .addPathPatterns("/**")
                // OpenAI 兼容接口使用独立的 API Key 鉴权；actuator / 错误页无需登录态。
                .excludePathPatterns("/v1/**", "/actuator/**", "/error");
    }
}
