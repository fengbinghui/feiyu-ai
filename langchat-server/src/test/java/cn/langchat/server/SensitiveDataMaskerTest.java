package cn.langchat.server;

import cn.langchat.common.core.audit.SensitiveDataMasker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计敏感信息脱敏工具单元测试。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
class SensitiveDataMaskerTest {

    @Test
    void masksPasswordField() {
        String json = "{\"username\":\"admin\",\"password\":\"123456\"}";
        String masked = SensitiveDataMasker.mask(json);
        assertTrue(masked.contains("\"password\":\"" + SensitiveDataMasker.MASK_VALUE + "\""), masked);
        assertFalse(masked.contains("123456"), masked);
        assertTrue(masked.contains("\"username\":\"admin\""), masked);
    }

    @Test
    void masksApiKeyAndOldPassword() {
        String json = "{\"apiKey\":\"sk-abcdef\",\"oldPassword\":\"old123\",\"newPassword\":\"new456\"}";
        String masked = SensitiveDataMasker.mask(json);
        assertFalse(masked.contains("sk-abcdef"), masked);
        assertFalse(masked.contains("old123"), masked);
        assertFalse(masked.contains("new456"), masked);
        assertEquals(3, countOccurrences(masked, SensitiveDataMasker.MASK_VALUE), masked);
    }

    @Test
    void masksAuthorizationAndTokenValue() {
        String json = "{\"authorization\":\"Bearer abc.def\",\"tokenValue\":\"ff5a9d1c\",\"token\":\"t-123\"}";
        String masked = SensitiveDataMasker.mask(json);
        assertFalse(masked.contains("Bearer abc.def"), masked);
        assertFalse(masked.contains("ff5a9d1c"), masked);
        assertFalse(masked.contains("t-123"), masked);
    }

    @Test
    void masksSecretKeyAndConfirmPassword() {
        String json = "{\"secretKey\":\"sec-1\",\"confirmPassword\":\"p@ss\"}";
        String masked = SensitiveDataMasker.mask(json);
        assertFalse(masked.contains("sec-1"), masked);
        assertFalse(masked.contains("p@ss"), masked);
    }

    @Test
    void keepsNonSensitiveContentUntouched() {
        String json = "{\"username\":\"admin\",\"type\":\"MODEL\",\"url\":\"/api/v1/aigc/models\"}";
        assertEquals(json, SensitiveDataMasker.mask(json));
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertNull(SensitiveDataMasker.mask(null));
        assertEquals("", SensitiveDataMasker.mask(""));
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
