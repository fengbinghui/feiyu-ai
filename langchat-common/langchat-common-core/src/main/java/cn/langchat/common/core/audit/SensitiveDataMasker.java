package cn.langchat.common.core.audit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏工具。
 *
 * <p>对 JSON / 文本中的 password、apiKey、token、authorization、secret 等敏感字段值做掩码，
 * 用于审计日志参数落库前处理，避免密钥与密码泄露。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
public final class SensitiveDataMasker {

    /** 脱敏替换值。 */
    public static final String MASK_VALUE = "******";

    /** 敏感字段名（按长度倒序，保证 oldPassword 先于 password 匹配）。 */
    private static final String SENSITIVE_KEY_PATTERN = String.join("|",
            "confirmPassword", "oldPassword", "newPassword", "password",
            "secretKey", "apiKey", "tokenValue", "authorization", "token", "secret"
    );

    /**
     * 匹配形如 "password":"xxx" / "apiKey": "xxx" / authorization=xxx 的敏感键值对。
     * 键名允许带引号或不带引号（表单/查询串场景），值支持带引号字符串或普通 token。
     */
    private static final Pattern SENSITIVE_PAIR_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])" +
                    "(\"?(?:" + SENSITIVE_KEY_PATTERN + ")\"?\\s*[:=]\\s*)" +
                    "(\"[^\"]*\"|'[^']*'|[^,}\\]\\s&]+)"
    );

    private SensitiveDataMasker() {
    }

    /**
     * 对文本中的敏感字段值做掩码；null/空串原样返回。
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = SENSITIVE_PAIR_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer(text.length());
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + "\"" + MASK_VALUE + "\""));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
