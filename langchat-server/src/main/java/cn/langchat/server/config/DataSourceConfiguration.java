package cn.langchat.server.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 双数据源配置。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@Configuration
public class DataSourceConfiguration {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.mysql")
    public DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "mysqlDataSource")
    @Primary
    @ConditionalOnProperty(prefix = "spring.datasource.mysql", name = "url")
    public DataSource mysqlDataSource(@Qualifier("mysqlDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean(name = "mysqlJdbcTemplate")
    @Primary
    @ConditionalOnBean(name = "mysqlDataSource")
    public JdbcTemplate mysqlJdbcTemplate(@Qualifier("mysqlDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Pgvector 数据源属性（可选）。
     *
     * <p>知识库向量检索由运行时的向量库配置驱动，这里的数据源仅用于
     * {@code DualDataSourceHealthIndicator} 等基础组件做连通性健康检查。</p>
     */
    @Bean
    @ConfigurationProperties("spring.datasource.pgvector")
    public DataSourceProperties pgvectorDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "pgvectorDataSource")
    @ConditionalOnProperty(prefix = "spring.datasource.pgvector", name = "url")
    public DataSource pgvectorDataSource(
            @Qualifier("pgvectorDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
}
