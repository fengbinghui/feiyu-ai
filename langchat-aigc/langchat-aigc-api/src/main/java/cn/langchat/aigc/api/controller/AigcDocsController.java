package cn.langchat.aigc.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.langchat.aigc.api.model.request.DocsSaveRequest;
import cn.langchat.aigc.biz.entity.AigcDocs;
import cn.langchat.aigc.biz.entity.AigcKnowledge;
import cn.langchat.aigc.biz.entity.AigcOss;
import cn.langchat.aigc.biz.event.AigcDocsDeletedEvent;
import cn.langchat.aigc.biz.service.AigcDocsService;
import cn.langchat.aigc.biz.service.AigcKnowledgeService;
import cn.langchat.aigc.biz.service.AigcOssService;
import cn.langchat.common.core.ApiResponse;
import cn.langchat.common.core.CommonErrorCode;
import cn.langchat.common.core.audit.AuditLog;
import cn.langchat.common.core.audit.AuditLogType;
import cn.langchat.common.core.page.PageQuery;
import cn.langchat.common.core.page.PageResult;
import cn.langchat.common.exception.BizException;
import cn.langchat.common.oss.model.OssObject;
import cn.langchat.common.oss.model.OssUploadRequest;
import cn.langchat.common.oss.service.OssService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档管理控制器。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@RestController
@RequestMapping("/api/v1/aigc/docs")
@RequiredArgsConstructor
@Slf4j
@SaCheckPermission("aigc:knowledge:list")
public class AigcDocsController {

    private static final String DEFAULT_DOC_TYPE = "FILE";
    private static final String DEFAULT_OSS_PLATFORM = "LOCAL";
    /** 单文件上传上限：100 MB。 */
    private static final long MAX_UPLOAD_SIZE = 100L * 1024 * 1024;
    /** 文档上传扩展名白名单。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "md", "markdown", "txt", "csv", "html", "htm", "json"
    );

    private final AigcDocsService aigcDocsService;
    private final AigcKnowledgeService aigcKnowledgeService;
    private final AigcOssService aigcOssService;
    private final OssService ossService;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping
    public ApiResponse<List<AigcDocs>> list(@RequestParam(value = "knowledgeId", required = false) String knowledgeId) {
        return ApiResponse.success(aigcDocsService.lambdaQuery()
                .eq(knowledgeId != null && !knowledgeId.isBlank(), AigcDocs::getKnowledgeId, knowledgeId)
                .orderByDesc(AigcDocs::getCreateTime)
                .list());
    }

    /**
     * 分页查询文档列表。
     *
     * @param pageNo 页码，默认 1
     * @param pageSize 页大小，默认 20、上限 200
     * @param keyword 关键字（文档名称模糊匹配）
     * @param knowledgeId 知识库 ID（精确匹配），保留原有列表接口的过滤能力
     * @param embedStatus 向量化状态（精确匹配，前端文档列表状态筛选）
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<AigcDocs>> page(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "knowledgeId", required = false) String knowledgeId,
            @RequestParam(value = "embedStatus", required = false) String embedStatus
    ) {
        PageQuery page = PageQuery.of(pageNo, pageSize);
        LambdaQueryWrapper<AigcDocs> wrapper = Wrappers.<AigcDocs>lambdaQuery()
                .eq(StringUtils.hasText(knowledgeId), AigcDocs::getKnowledgeId, knowledgeId)
                .like(StringUtils.hasText(keyword), AigcDocs::getName, keyword)
                .eq(StringUtils.hasText(embedStatus), AigcDocs::getEmbedStatus, embedStatus)
                .orderByDesc(AigcDocs::getCreateTime);
        Page<AigcDocs> result = aigcDocsService.page(
                new Page<>(page.getPageNo(), page.getPageSize()),
                wrapper
        );
        return ApiResponse.success(PageResult.of(
                result.getRecords(),
                result.getTotal(),
                page.getPageNo(),
                page.getPageSize()
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<AigcDocs> detail(@PathVariable("id") String id) {
        return ApiResponse.success(aigcDocsService.getById(id));
    }

    /**
     * 上传知识库文档。
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AuditLog(type = AuditLogType.DOC, action = "上传知识库文档")
    public ApiResponse<AigcDocs> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeId") String knowledgeId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "ingestionConfig", required = false) String ingestionConfig
    ) throws IOException {
        validateUploadRequest(file, knowledgeId);
        requireKnowledge(knowledgeId);

        String originalFilename = resolveOriginalFilename(file);
        String extension = resolveExtension(originalFilename);
        OssObject ossObject = ossService.upload(new OssUploadRequest(
                originalFilename,
                file.getContentType(),
                file.getBytes(),
                java.util.Map.of("knowledgeId", knowledgeId)
        ));

        AigcOss oss = buildOssEntity(file, originalFilename, extension, ossObject);
        aigcOssService.save(oss);

        AigcDocs docs = buildDocsEntity(
                knowledgeId,
                oss,
                file,
                extension,
                name,
                type,
                parentId,
                enabled,
                ingestionConfig
        );
        aigcDocsService.save(docs);
        log.info(
                "上传知识库文档成功，knowledgeId={}, docsId={}, ossId={}, originalFilename={}",
                knowledgeId,
                docs.getId(),
                oss.getId(),
                originalFilename
        );
        return ApiResponse.success(docs);
    }

    @PostMapping
    public ApiResponse<Boolean> create(@Valid @RequestBody DocsSaveRequest request) {
        AigcDocs docs = new AigcDocs();
        BeanUtils.copyProperties(request, docs);
        log.info("新增文档，name={}", docs.getName());
        return ApiResponse.success(aigcDocsService.save(docs));
    }

    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(
            @PathVariable("id") String id,
            @Valid @RequestBody DocsSaveRequest request
    ) {
        AigcDocs docs = new AigcDocs();
        BeanUtils.copyProperties(request, docs);
        docs.setId(id);
        log.info("更新文档，id={}", id);
        return ApiResponse.success(aigcDocsService.updateById(docs));
    }

    @DeleteMapping("/{id}")
    @AuditLog(type = AuditLogType.DOC, action = "删除知识库文档")
    public ApiResponse<Boolean> remove(@PathVariable("id") String id) {
        AigcDocs docs = aigcDocsService.getById(id);
        boolean removed = aigcDocsService.removeById(id);
        if (removed && docs != null) {
            // 文档记录删除成功后发布事件，由 langchat-core 清理该文档的分段与向量库残留；
            // 清理失败不影响删除结果（监听器内部已收敛异常）。
            try {
                eventPublisher.publishEvent(new AigcDocsDeletedEvent(docs.getId(), docs.getKnowledgeId()));
            } catch (Exception ex) {
                log.warn("发布文档删除清理事件失败（不影响删除结果），docsId={}", id, ex);
            }
        }
        log.info("删除文档，id={}, removed={}", id, removed);
        return ApiResponse.success(removed);
    }

    private void validateUploadRequest(MultipartFile file, String knowledgeId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "上传文件不能为空");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "单个文档不能超过 100 MB");
        }
        String extension = resolveExtension(resolveOriginalFilename(file));
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(
                    CommonErrorCode.BAD_REQUEST.code(),
                    "不支持的文件类型：" + (extension.isEmpty() ? "未知" : extension)
            );
        }
        if (!StringUtils.hasText(knowledgeId)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "知识库 ID 不能为空");
        }
    }

    private void requireKnowledge(String knowledgeId) {
        AigcKnowledge knowledge = aigcKnowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "知识库不存在");
        }
    }

    private AigcOss buildOssEntity(
            MultipartFile file,
            String originalFilename,
            String extension,
            OssObject ossObject
    ) {
        AigcOss oss = new AigcOss();
        oss.setFilename(ossObject.objectKey());
        oss.setOriginalFilename(originalFilename);
        oss.setUrl(ossObject.url());
        oss.setPath(ossObject.url());
        oss.setSize(safeFileSize(file.getSize()));
        oss.setExt(extension);
        oss.setContentType(file.getContentType());
        oss.setPlatform(ossObject.platform() == null ? DEFAULT_OSS_PLATFORM : ossObject.platform());
        return oss;
    }

    private AigcDocs buildDocsEntity(
            String knowledgeId,
            AigcOss oss,
            MultipartFile file,
            String extension,
            String name,
            String type,
            String parentId,
            Boolean enabled,
            String ingestionConfig
    ) {
        AigcDocs docs = new AigcDocs();
        docs.setKnowledgeId(knowledgeId);
        docs.setOssId(oss.getId());
        docs.setParentId(parentId);
        docs.setEnabled(enabled == null ? Boolean.TRUE : enabled);
        docs.setIndexingStatus(0);
        docs.setEmbedStatus("pending");
        docs.setEmbedError(null);
        docs.setEmbedStartTime(null);
        docs.setEmbedEndTime(null);
        docs.setIngestionConfig(ingestionConfig);
        docs.setType(resolveDocsType(type, extension));
        docs.setName(StringUtils.hasText(name) ? name : resolveDisplayName(file));
        docs.setExt(extension);
        docs.setUrl(oss.getPath());
        docs.setPdfUrl("pdf".equalsIgnoreCase(extension) ? oss.getPath() : null);
        docs.setContent(null);
        docs.setSize(safeFileSize(file.getSize()));
        return docs;
    }

    private String resolveDocsType(String type, String extension) {
        if (StringUtils.hasText(type)) {
            return type.trim().toUpperCase();
        }
        return switch (extension.toLowerCase()) {
            case "md", "markdown" -> "MARKDOWN";
            case "txt", "text" -> "TEXT";
            default -> DEFAULT_DOC_TYPE;
        };
    }

    private String resolveOriginalFilename(MultipartFile file) {
        return StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename().trim() : "upload.bin";
    }

    private String resolveDisplayName(MultipartFile file) {
        String originalFilename = resolveOriginalFilename(file);
        int index = originalFilename.lastIndexOf('.');
        return index > 0 ? originalFilename.substring(0, index) : originalFilename;
    }

    private String resolveExtension(String originalFilename) {
        int index = originalFilename.lastIndexOf('.');
        if (index < 0 || index == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private int safeFileSize(long fileSize) {
        return fileSize > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) fileSize;
    }
}
