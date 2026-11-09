package cn.langchat.server;

import cn.langchat.auth.config.AuthProperties;
import cn.langchat.auth.security.LoginAttemptService;
import cn.langchat.common.exception.BizException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 登录失败风控单元测试（纯单元测试，不启动 Spring / 不连 DB）。
 *
 * <p>通过反射注入固定时钟，验证失败锁定、成功清零、窗口过期与锁定过期逻辑。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
class LoginAttemptServiceTest {

    private static final long WINDOW_MINUTES = 10L;
    private static final long LOCK_MINUTES = 10L;
    private static final long ONE_MINUTE_MILLIS = 60_000L;

    private LoginAttemptService service;
    private AtomicLong now;

    @BeforeEach
    void setUp() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setLoginMaxAttempts(2);
        properties.setLoginFailWindowMinutes((int) WINDOW_MINUTES);
        properties.setLoginLockMinutes((int) LOCK_MINUTES);
        service = new LoginAttemptService(properties);

        now = new AtomicLong(1_700_000_000_000L);
        LongSupplier clock = now::get;
        Field clockField = LoginAttemptService.class.getDeclaredField("clock");
        clockField.setAccessible(true);
        clockField.set(service, clock);
    }

    @Test
    void locksAfterMaxConsecutiveFailures() {
        service.recordFailure("admin");
        assertThrows(BizException.class, () -> service.recordFailure("admin"));

        BizException exception = assertThrows(BizException.class, () -> service.checkAllowed("admin"));
        assertEquals(LoginAttemptService.RATE_LIMIT_CODE, exception.getCode());
    }

    @Test
    void successClearsFailureCounter() {
        service.recordFailure("user1");
        service.recordSuccess("user1");
        assertDoesNotThrow(() -> service.checkAllowed("user1"));

        // 计数已清零，再失败一次不应触发锁定
        service.recordFailure("user1");
        assertDoesNotThrow(() -> service.checkAllowed("user1"));
    }

    @Test
    void failureWindowExpiryResetsCounter() {
        service.recordFailure("user2");
        now.addAndGet((WINDOW_MINUTES + 1) * ONE_MINUTE_MILLIS);
        // 窗口已过期：本次失败应重新从 1 计数，不应锁定
        assertDoesNotThrow(() -> service.recordFailure("user2"));
        assertDoesNotThrow(() -> service.checkAllowed("user2"));

        // 窗口内再失败一次达到上限 -> 锁定
        assertThrows(BizException.class, () -> service.recordFailure("user2"));
        assertThrows(BizException.class, () -> service.checkAllowed("user2"));
    }

    @Test
    void lockExpiresAfterLockMinutes() {
        service.recordFailure("user3");
        assertThrows(BizException.class, () -> service.recordFailure("user3"));
        assertThrows(BizException.class, () -> service.checkAllowed("user3"));

        now.addAndGet((LOCK_MINUTES + 1) * ONE_MINUTE_MILLIS);
        assertDoesNotThrow(() -> service.checkAllowed("user3"));
    }

    @Test
    void usernamesAreNormalizedToLowerCase() {
        service.recordFailure("Admin");
        assertThrows(BizException.class, () -> service.recordFailure("admin"));
        assertThrows(BizException.class, () -> service.checkAllowed("ADMIN"));
    }
}
