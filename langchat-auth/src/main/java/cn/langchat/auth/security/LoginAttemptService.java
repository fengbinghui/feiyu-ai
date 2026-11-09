package cn.langchat.auth.security;

import cn.langchat.auth.config.AuthProperties;
import cn.langchat.common.exception.BizException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 登录失败风控（单机内存实现，无需 Redis）。
 *
 * <p>按 username 维度统计连续登录失败次数：窗口内失败达到上限后锁定账号一段时间，
 * 锁定期内的登录请求直接抛出 {@code AUTH_LOGIN_RATE_LIMIT}（全局异常处理器映射为 HTTP 429）；
 * 登录成功后立即清零计数。参数可通过 {@code langchat.auth.login-max-attempts} /
 * {@code langchat.auth.login-fail-window-minutes} / {@code langchat.auth.login-lock-minutes} 配置。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptService {

    /** 触发限流的业务错误码（全局异常处理器据此返回 HTTP 429）。 */
    public static final String RATE_LIMIT_CODE = "AUTH_LOGIN_RATE_LIMIT";

    private final AuthProperties authProperties;

    /** 当前时间来源（便于单元测试注入固定时钟）。 */
    private LongSupplier clock = System::currentTimeMillis;

    /** username（小写归一化） -> 失败记录。 */
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * 登录前置校验：命中锁定期时直接抛出限流异常。
     */
    public void checkAllowed(String username) {
        if (username == null) {
            return;
        }
        String key = key(username);
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return;
        }
        long now = clock.getAsLong();
        Long lockUntil = attempt.lockUntil;
        if (lockUntil == null) {
            return;
        }
        if (lockUntil > now) {
            long remainSeconds = Math.max(1, (lockUntil - now + 999) / 1000);
            log.info("登录命中锁定风控，username={}, remainSeconds={}", username, remainSeconds);
            throw new BizException(RATE_LIMIT_CODE, "登录失败次数过多，账号已锁定，请 " + remainSeconds + " 秒后重试");
        }
        // 锁定期已过，清理过期记录，允许重新尝试。
        attempts.remove(key, attempt);
    }

    /**
     * 登录成功：清除该账号的失败计数。
     */
    public void recordSuccess(String username) {
        if (username == null) {
            return;
        }
        attempts.remove(key(username));
    }

    /**
     * 清除指定账号的失败计数与锁定状态（修改密码 / 管理员重置密码后调用）。
     */
    public void clear(String username) {
        if (username == null) {
            return;
        }
        attempts.remove(key(username));
        log.info("已清除账号登录失败计数与锁定状态，username={}", username);
    }

    /**
     * 登录失败：累加失败次数，达到上限时锁定账号并抛出限流异常。
     */
    public void recordFailure(String username) {
        if (username == null) {
            return;
        }
        String key = key(username);
        long now = clock.getAsLong();
        int maxAttempts = Math.max(1, authProperties.getLoginMaxAttempts());
        long windowMillis = Math.max(1, authProperties.getLoginFailWindowMinutes()) * 60_000L;
        long lockMillis = Math.max(1, authProperties.getLoginLockMinutes()) * 60_000L;

        Attempt attempt = attempts.compute(key, (k, existing) -> {
            if (existing == null || now - existing.firstFailAt > windowMillis || existing.lockUntil != null) {
                return new Attempt(1, now, null);
            }
            existing.failCount++;
            if (existing.failCount >= maxAttempts) {
                existing.lockUntil = now + lockMillis;
            }
            return existing;
        });

        Long lockUntil = attempt.lockUntil;
        if (lockUntil != null && lockUntil > now) {
            long remainSeconds = Math.max(1, (lockUntil - now + 999) / 1000);
            log.info("登录失败达到上限，账号已锁定，username={}, remainSeconds={}", username, remainSeconds);
            throw new BizException(RATE_LIMIT_CODE, "登录失败次数过多，账号已锁定，请 " + remainSeconds + " 秒后重试");
        }
    }

    private String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 单个账号的失败记录。
     */
    private static final class Attempt {

        private int failCount;
        private final long firstFailAt;
        private Long lockUntil;

        private Attempt(int failCount, long firstFailAt, Long lockUntil) {
            this.failCount = failCount;
            this.firstFailAt = firstFailAt;
            this.lockUntil = lockUntil;
        }
    }
}
