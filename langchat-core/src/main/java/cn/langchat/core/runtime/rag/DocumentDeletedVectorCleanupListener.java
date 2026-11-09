package cn.langchat.core.runtime.rag;

import cn.langchat.aigc.biz.entity.AigcKnowledge;
import cn.langchat.aigc.biz.entity.AigcSegment;
import cn.langchat.aigc.biz.entity.AigcVectorStore;
import cn.langchat.aigc.biz.event.AigcDocsDeletedEvent;
import cn.langchat.aigc.biz.service.AigcKnowledgeService;
import cn.langchat.aigc.biz.service.AigcSegmentService;
import cn.langchat.aigc.biz.service.AigcVectorStoreService;
import cn.langchat.core.runtime.factory.VectorStoreFactory;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 文档删除后的分段与向量清理监听器。
 *
 * <p>AigcDocsController 删除文档记录成功后发布 {@link AigcDocsDeletedEvent}，
 * 本监听器负责清理该文档在 MySQL {@code aigc_segment} 与向量库中的残留数据，
 * 避免删除文档后 RAG 仍能召回旧内容。</p>
 *
 * <p>清理过程中的异常全部在此收敛：只记录日志，不会向上抛出，因此不会影响
 * 文档删除接口本身的成功返回。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentDeletedVectorCleanupListener {

    private final AigcSegmentService aigcSegmentService;
    private final AigcKnowledgeService aigcKnowledgeService;
    private final AigcVectorStoreService aigcVectorStoreService;
    private final VectorStoreFactory vectorStoreFactory;

    @EventListener
    public void onDocumentDeleted(AigcDocsDeletedEvent event) {
        String docsId = event == null ? null : event.docsId();
        String knowledgeId = event == null ? null : event.knowledgeId();
        if (!StringUtils.hasText(docsId)) {
            return;
        }
        try {
            List<String> segmentIds = aigcSegmentService.lambdaQuery()
                    .eq(AigcSegment::getDocsId, docsId)
                    .list()
                    .stream()
                    .map(AigcSegment::getId)
                    .toList();
            boolean vectorCleaned = cleanupVectors(knowledgeId, docsId, segmentIds);
            if (!segmentIds.isEmpty()) {
                aigcSegmentService.removeByIds(segmentIds);
            }
            log.info(
                    "文档删除后清理完成，docsId={}, knowledgeId={}, segmentCount={}, vectorCleaned={}",
                    docsId,
                    knowledgeId,
                    segmentIds.size(),
                    vectorCleaned
            );
        } catch (Exception ex) {
            log.error("文档删除后清理分段/向量失败（不阻断文档删除），docsId={}, knowledgeId={}", docsId, knowledgeId, ex);
        }
    }

    /**
     * 清理向量库中该文档的向量：优先按旧分段 id 删除，再按 docsId 元数据兜底；
     * 知识库/向量库配置缺失或清理失败时返回 false（调用方仍会清理 MySQL 分段）。
     */
    private boolean cleanupVectors(String knowledgeId, String docsId, List<String> segmentIds) {
        AigcKnowledge knowledge = StringUtils.hasText(knowledgeId) ? aigcKnowledgeService.getById(knowledgeId) : null;
        if (knowledge == null || !StringUtils.hasText(knowledge.getVectorStoreId())) {
            log.warn("文档删除后未找到知识库/向量库配置，跳过向量清理，docsId={}, knowledgeId={}", docsId, knowledgeId);
            return false;
        }
        AigcVectorStore vectorStore = aigcVectorStoreService.getById(knowledge.getVectorStoreId());
        if (vectorStore == null) {
            log.warn("文档删除后未找到向量库配置，跳过向量清理，docsId={}, vectorStoreId={}", docsId, knowledge.getVectorStoreId());
            return false;
        }
        try {
            EmbeddingStore<TextSegment> embeddingStore = vectorStoreFactory.getStore(vectorStore);
            if (!segmentIds.isEmpty()) {
                embeddingStore.removeAll(segmentIds);
            }
            try {
                embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("docsId").isEqualTo(docsId));
            } catch (Exception ex) {
                log.warn("按 docsId 兜底清理向量失败（忽略），docsId={}", docsId, ex);
            }
            return true;
        } catch (Exception ex) {
            log.warn("向量库清理失败，仅清理 MySQL 分段，docsId={}", docsId, ex);
            return false;
        }
    }
}
