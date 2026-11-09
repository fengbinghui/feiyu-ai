package cn.langchat.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 文档配置。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Configuration
public class OpenApiConfiguration {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI feiyuOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("肥鱼AI API")
                        .version("1.0.0")
                        .description("肥鱼AI（企业级 AI Agent 应用平台）后端接口文档。"
                                + "受保护接口需在 Authorization 请求头携带登录返回的 token。"))
                .components(new Components().addSecuritySchemes(
                        SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")
                                .description("登录接口返回的 token（tokenName=Authorization）")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
