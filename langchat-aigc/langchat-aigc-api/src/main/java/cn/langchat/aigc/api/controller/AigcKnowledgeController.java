package cn.langchat.aigc.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.langchat.aigc.api.model.request.KnowledgeSaveRequest;
import cn.langchat.aigc.biz.entity.AigcKnowledge;
import cn.langchat.aigc.biz.service.AigcKnowledgeService;
import cn.langchat.common.core.ApiResponse;
import cn.langchat.common.core.audit.AuditLog;
import cn.langchat.common.core.audit.AuditLogType;
import cn.langchat.common.core.page.PageQuery;
import cn.langchat.common.core.page.PageResult;
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
 * 知识库管理控制器。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@RestController
@RequestMapping("/api/v1/aigc/knowledges")
@RequiredArgsConstructor
@Slf4j
@SaCheckPermission("aigc:knowledge:list")
public class AigcKnowledgeController {

    private final AigcKnowledgeService aigcKnowledgeService;

    /**
     * 查询知识库全量列表（保留给下拉框/关联选择使用）。
     */
    @GetMapping
    public ApiResponse<List<AigcKnowledge>> list() {
        return ApiResponse.success(aigcKnowledgeService.lambdaQuery().orderByDesc(AigcKnowledge::getCreateTime).list());
    }

    /**
     * 分页查询知识库列表。
     *
     * @param pageNo 页码，默认 1
     * @param pageSize 页大小，默认 20、上限 200
     * @param keyword 关键字（知识库名称 / 描述模糊匹配）
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<AigcKnowledge>> page(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        PageQuery page = PageQuery.of(pageNo, pageSize);
        LambdaQueryWrapper<AigcKnowledge> wrapper = Wrappers.<AigcKnowledge>lambdaQuery()
                .and(StringUtils.hasText(keyword), query -> query
                        .like(AigcKnowledge::getName, keyword)
                        .or().like(AigcKnowledge::getDescription, keyword))
                .orderByDesc(AigcKnowledge::getCreateTime);
        Page<AigcKnowledge> result = aigcKnowledgeService.page(
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

    /**
     * 查询知识库详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<AigcKnowledge> detail(@PathVariable("id") String id) {
        return ApiResponse.success(aigcKnowledgeService.getById(id));
    }

    /**
     * 新增知识库。
     */
    @PostMapping
    @AuditLog(type = AuditLogType.KNOWLEDGE, action = "新增知识库")
    public ApiResponse<Boolean> create(@Valid @RequestBody KnowledgeSaveRequest request) {
        AigcKnowledge knowledge = new AigcKnowledge();
        BeanUtils.copyProperties(request, knowledge);
        log.info("新增知识库，name={}", knowledge.getName());
        return ApiResponse.success(aigcKnowledgeService.save(knowledge));
    }

    /**
     * 更新知识库。
     */
    @PutMapping("/{id}")
    @AuditLog(type = AuditLogType.KNOWLEDGE, action = "更新知识库")
    public ApiResponse<Boolean> update(
            @PathVariable("id") String id,
            @Valid @RequestBody KnowledgeSaveRequest request
    ) {
        AigcKnowledge knowledge = new AigcKnowledge();
        BeanUtils.copyProperties(request, knowledge);
        knowledge.setId(id);
        log.info("更新知识库，id={}", id);
        return ApiResponse.success(aigcKnowledgeService.updateById(knowledge));
    }

    /**
     * 删除知识库。
     */
    @DeleteMapping("/{id}")
    @AuditLog(type = AuditLogType.KNOWLEDGE, action = "删除知识库")
    public ApiResponse<Boolean> remove(@PathVariable("id") String id) {
        log.info("删除知识库，id={}", id);
        return ApiResponse.success(aigcKnowledgeService.removeById(id));
    }
}
