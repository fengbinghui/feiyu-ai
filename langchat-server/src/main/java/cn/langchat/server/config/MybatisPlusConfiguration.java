package cn.langchat.server.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>注册分页插件：{@code IService.page(...)} / {@code BaseMapper.selectPage(...)}
 * 依赖该内部拦截器改写 SQL 并回填总数，缺失时分页参数不会生效（会退化为全表查询）。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Configuration
public class MybatisPlusConfiguration {

    /** 单页最大条数兜底，避免超大 pageSize 拖垮数据库。 */
    private static final long MAX_LIMIT = 500L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_LIMIT);
        // 超过最大页时返回空集合而不是回到第一页
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
