package cn.langchat.common.core.audit;

/**
 * 操作审计日志类型。
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
public enum AuditLogType {

    /** 登录认证。 */
    LOGIN,

    /** 用户管理。 */
    USER,

    /** 模型管理。 */
    MODEL,

    /** Agent 管理。 */
    AGENT,

    /** 知识库。 */
    KNOWLEDGE,

    /** 知识库文档。 */
    DOC,

    /** 技能包。 */
    SKILL,

    /** MCP 配置。 */
    MCP,

    /** 向量库。 */
    VECTOR_STORE,

    /** 数据源。 */
    DATASOURCE,

    /** 对话相关操作。 */
    CHAT,

    /** 其他。 */
    OTHER
}
