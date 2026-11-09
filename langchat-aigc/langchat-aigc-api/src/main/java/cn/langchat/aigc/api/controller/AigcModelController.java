package cn.langchat.aigc.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.langchat.aigc.api.model.request.ModelSaveRequest;
import cn.langchat.aigc.biz.entity.AigcModel;
import cn.langchat.aigc.biz.service.AigcModelService;
import cn.langchat.aigc.biz.support.ModelConfigSupport;
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
import java.util.Locale;
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
 * 模型管理控制器。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@RestController
@RequestMapping("/api/v1/aigc/models")
@RequiredArgsConstructor
@Slf4j
@SaCheckPermission("aigc:model:list")
public class AigcModelController {

    private final AigcModelService aigcModelService;

    /**
     * 查询模型全量列表（保留给下拉框/关联选择使用）。
     */
    @GetMapping
    public ApiResponse<List<AigcModel>> list() {
        List<AigcModel> models = aigcModelService.lambdaQuery().orderByDesc(AigcModel::getCreateTime).list();
        // 敏感字段脱敏：接口不返回完整 apiKey。
        models.forEach(model -> model.setApiKey(maskSecret(model.getApiKey())));
        return ApiResponse.success(models);
    }

    /**
     * 分页查询模型列表（服务端分页）。
     *
     * @param pageNo 页码，默认 1
     * @param pageSize 页大小，默认 20、上限 200
     * @param keyword 关键字（模型名称 / 别名模糊匹配）
     * @param type 模型类型（精确匹配，与实体字段一致，如 TEXT2TEXT / EMBEDDINGS）
     * @param modelType 模型类型（契约参数名，兼容 type）
     * @param provider 模型供应商（精确匹配）
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<AigcModel>> page(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "modelType", required = false) String modelType,
            @RequestParam(value = "provider", required = false) String provider
    ) {
        PageQuery page = PageQuery.of(pageNo, pageSize);
        // 契约中模型过滤参数名为 modelType，任务说明与实体列名使用 type，两者都兼容。
        String resolvedType = StringUtils.hasText(type) ? type : modelType;
        LambdaQueryWrapper<AigcModel> wrapper = Wrappers.<AigcModel>lambdaQuery()
                .eq(StringUtils.hasText(resolvedType), AigcModel::getType, resolvedType)
                .eq(StringUtils.hasText(provider), AigcModel::getProvider, provider)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(AigcModel::getModel, keyword)
                        .or().like(AigcModel::getName, keyword))
                .orderByDesc(AigcModel::getCreateTime);
        Page<AigcModel> result = aigcModelService.page(
                new Page<>(page.getPageNo(), page.getPageSize()),
                wrapper
        );
        // 敏感字段脱敏：分页结果同样不返回完整 apiKey。
        result.getRecords().forEach(model -> model.setApiKey(maskSecret(model.getApiKey())));
        return ApiResponse.success(PageResult.of(
                result.getRecords(),
                result.getTotal(),
                page.getPageNo(),
                page.getPageSize()
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<AigcModel> detail(@PathVariable("id") String id) {
        AigcModel model = aigcModelService.getById(id);
        if (model != null) {
            model.setApiKey(maskSecret(model.getApiKey()));
        }
        return ApiResponse.success(model);
    }

    @PostMapping
    @AuditLog(type = AuditLogType.MODEL, action = "新增模型")
    public ApiResponse<Boolean> create(@Valid @RequestBody ModelSaveRequest request) {
        AigcModel model = new AigcModel();
        BeanUtils.copyProperties(request, model);
        log.info("新增模型，provider={}, model={}", model.getProvider(), model.getModel());
        validateModelConfig(model);
        return ApiResponse.success(aigcModelService.save(model));
    }

    @PutMapping("/{id}")
    @AuditLog(type = AuditLogType.MODEL, action = "更新模型")
    public ApiResponse<Boolean> update(@PathVariable("id") String id, @Valid @RequestBody ModelSaveRequest request) {
        AigcModel model = new AigcModel();
        BeanUtils.copyProperties(request, model);
        model.setId(id);
        log.info("更新模型，id={}", id);
        // 前端回显的是脱敏后的 apiKey（含 *）或未传，此时保留数据库中的原始密钥。
        if (isMaskedOrBlank(model.getApiKey())) {
            AigcModel existing = aigcModelService.getById(id);
            model.setApiKey(existing == null ? null : existing.getApiKey());
        }
        validateModelConfig(model);
        return ApiResponse.success(aigcModelService.updateById(model));
    }

    @DeleteMapping("/{id}")
    @AuditLog(type = AuditLogType.MODEL, action = "删除模型")
    public ApiResponse<Boolean> remove(@PathVariable("id") String id) {
        log.info("删除模型，id={}", id);
        return ApiResponse.success(aigcModelService.removeById(id));
    }

    private void validateModelConfig(AigcModel model) {
        String type = model.getType() == null
                ? ""
                : model.getType().trim().toUpperCase(Locale.ROOT);
        if (("EMBEDDINGS".equals(type) || "EMBEDDING".equals(type))
                && ModelConfigSupport.resolveDimension(model) == null) {
            throw new BizException(
                    CommonErrorCode.BAD_REQUEST.code(),
                    "向量模型 configJson.dimension 必须配置为大于 0 的整数"
            );
        }
    }

    /**
     * 敏感信息脱敏：保留前 3 位与后 4 位，过短的密钥直接返回 ****。
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

    /**
     * 判断提交的密钥是否为脱敏值或空值（此时应保留原值）。
     */
    private boolean isMaskedOrBlank(String apiKey) {
        return !StringUtils.hasText(apiKey) || apiKey.contains("*");
    }
}
