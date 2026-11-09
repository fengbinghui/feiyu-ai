package cn.langchat.aigc.biz.event;

/**
 * 文档删除事件。文档记录删除成功后发布，
 * 由运行时监听清理该文档在 MySQL 分段表与向量库中的残留数据，
 * 避免删除文档后 RAG 仍能召回旧内容。
 *
 * @param docsId 文档主键
 * @param knowledgeId 文档所属知识库主键
 * @author LangChat Team
 * @since 2026/11/9
 */
public record AigcDocsDeletedEvent(String docsId, String knowledgeId) {
}
