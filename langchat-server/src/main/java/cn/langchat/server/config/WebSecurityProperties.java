package cn.langchat.server.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Web 安全相关配置属性。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Data
@Component
@ConfigurationProperties(prefix = "langchat.web")
public class WebSecurityProperties {

    /**
     * 允许跨域访问的前端来源（逗号分隔）。
     *
     * <p>默认仅放行本地前端开发端口，避免默认放开全部来源。</p>
     */
    private List<String> corsAllowedOrigins = new ArrayList<>(List.of(
            "http://localhost:5888",
            "http://127.0.0.1:5888"
    ));
}
