package cn.langchat.aigc.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.langchat.aigc.api.model.request.VectorStoreSaveRequest;
import cn.langchat.aigc.biz.entity.AigcVectorStore;
import cn.langchat.aigc.biz.service.AigcVectorStoreService;
import cn.langchat.aigc.biz.support.VectorStoreSupport;
import cn.langchat.common.core.ApiResponse;
import cn.langchat.common.core.CommonErrorCode;
import cn.langchat.common.core.audit.AuditLog;
import cn.langchat.common.core.audit.AuditLogType;
import cn.langchat.common.core.page.PageQuery;
import cn.langchat.common.core.page.PageResult;
import cn.langchat.common.exception.BizException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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

/**
 * 向量库管理控制器。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@RestController
@RequestMapping("/api/v1/aigc/vector-stores")
@RequiredArgsConstructor
@Slf4j
@SaCheckPermission("aigc:vector-store:list")
public class AigcVectorStoreController {

    private final AigcVectorStoreService aigcVectorStoreService;

    @GetMapping
    public ApiResponse<List<AigcVectorStore>> list() {
        List<AigcVectorStore> vectorStores =
                aigcVectorStoreService.lambdaQuery().orderByDesc(AigcVectorStore::getCreateTime).list();
        // 敏感字段脱敏：接口不返回完整密码。
        vectorStores.forEach(store -> store.setPassword(maskSecret(store.getPassword())));
        return ApiResponse.success(vectorStores);
    }

    /**
     * 分页查询向量库列表（服务端分页）。
     *
     * @param pageNo 页码，默认 1
     * @param pageSize 页大小，默认 20、上限 200
     * @param keyword 关键字（向量库名称模糊匹配）
     * @param provider 供应商（精确匹配，前端供应商筛选）
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<AigcVectorStore>> page(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "provider", required = false) String provider
    ) {
        PageQuery page = PageQuery.of(pageNo, pageSize);
        LambdaQueryWrapper<AigcVectorStore> wrapper = Wrappers.<AigcVectorStore>lambdaQuery()
                .like(StringUtils.hasText(keyword), AigcVectorStore::getName, keyword)
                .eq(StringUtils.hasText(provider), AigcVectorStore::getProvider, provider)
                .orderByDesc(AigcVectorStore::getCreateTime);
        Page<AigcVectorStore> result = aigcVectorStoreService.page(
                new Page<>(page.getPageNo(), page.getPageSize()),
                wrapper
        );
        // 敏感字段脱敏：分页结果同样不返回完整密码。
        result.getRecords().forEach(store -> store.setPassword(maskSecret(store.getPassword())));
        return ApiResponse.success(PageResult.of(
                result.getRecords(),
                result.getTotal(),
                page.getPageNo(),
                page.getPageSize()
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<AigcVectorStore> detail(@PathVariable("id") String id) {
        AigcVectorStore vectorStore = aigcVectorStoreService.getById(id);
        if (vectorStore != null) {
            vectorStore.setPassword(maskSecret(vectorStore.getPassword()));
        }
        return ApiResponse.success(vectorStore);
    }

    @PostMapping
    @AuditLog(type = AuditLogType.VECTOR_STORE, action = "新增向量库")
    public ApiResponse<Boolean> create(@Valid @RequestBody VectorStoreSaveRequest request) {
        AigcVectorStore vectorStore = new AigcVectorStore();
        BeanUtils.copyProperties(request, vectorStore);
        log.info("新增向量库，name={}, provider={}", vectorStore.getName(), vectorStore.getProvider());
        validateVectorStore(vectorStore);
        return ApiResponse.success(aigcVectorStoreService.save(vectorStore));
    }

    @PutMapping("/{id}")
    @AuditLog(type = AuditLogType.VECTOR_STORE, action = "更新向量库")
    public ApiResponse<Boolean> update(
            @PathVariable("id") String id,
            @Valid @RequestBody VectorStoreSaveRequest request
    ) {
        AigcVectorStore vectorStore = new AigcVectorStore();
        BeanUtils.copyProperties(request, vectorStore);
        vectorStore.setId(id);
        log.info("更新向量库，id={}", id);
        // 前端回显的是脱敏后的密码（含 *）或未传，此时保留数据库中的原始密码。
        if (isMaskedOrBlank(vectorStore.getPassword())) {
            AigcVectorStore existing = aigcVectorStoreService.getById(id);
            vectorStore.setPassword(existing == null ? null : existing.getPassword());
        }
        validateVectorStore(vectorStore);
        return ApiResponse.success(aigcVectorStoreService.updateById(vectorStore));
    }

    @DeleteMapping("/{id}")
    @AuditLog(type = AuditLogType.VECTOR_STORE, action = "删除向量库")
    public ApiResponse<Boolean> remove(@PathVariable("id") String id) {
        log.info("删除向量库，id={}", id);
        return ApiResponse.success(aigcVectorStoreService.removeById(id));
    }

    /**
     * 校验向量库配置必填项，避免保存后向量化时才因缺少表名/维度等配置失败。
     */
    private void validateVectorStore(AigcVectorStore vectorStore) {
        if (vectorStore.getName() == null || vectorStore.getName().isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "向量库名称不能为空");
        }
        if (vectorStore.getProvider() == null || vectorStore.getProvider().isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "向量库供应商不能为空");
        }
        if (vectorStore.getHost() == null || vectorStore.getHost().isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "向量库主机地址不能为空");
        }
        if (VectorStoreSupport.requiresTableName(vectorStore)
                && (vectorStore.getTableName() == null || vectorStore.getTableName().isBlank())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "向量库表名/集合名不能为空");
        }
        if (vectorStore.getDimension() == null || vectorStore.getDimension() <= 0) {
            throw new BizException(CommonErrorCode.BAD_REQUEST.code(), "向量库维度必须大于 0");
        }
    }

    /**
     * 敏感信息脱敏：保留前 3 位与后 4 位，过短的密码直接返回 ****。
     */
    private String maskSecret(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String secret = value.trim();
        if (secret.length() < 8) {
            return "****";
        }
        return secret.substring(0, 3) + "****" + secret.substring(secret.length() - 4);
    }

    private boolean isMaskedOrBlank(String password) {
        return !StringUtils.hasText(password) || password.contains("*");
    }
}
