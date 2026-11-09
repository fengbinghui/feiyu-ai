package cn.langchat.server.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 跨域配置。
 *
 * <p>使用 Servlet 过滤器而非 MVC 映射，保证预检请求在进入登录拦截器之前被直接响应，
 * 同时也让 401/403 等错误响应能够带上跨域头，便于前端读取错误信息。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Configuration
@RequiredArgsConstructor
public class WebCorsConfiguration {

    private final WebSecurityProperties webSecurityProperties;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(webSecurityProperties.getCorsAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Origin", "X-Request-Id", "X-Trace-Id"
        ));
        configuration.setExposedHeaders(List.of(
                "Authorization", "X-Request-Id", "X-Trace-Id", "Content-Disposition"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
