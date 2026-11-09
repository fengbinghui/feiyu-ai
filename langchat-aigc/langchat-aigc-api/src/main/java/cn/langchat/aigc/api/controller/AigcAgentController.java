package cn.langchat.aigc.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.langchat.aigc.api.model.request.AgentSaveRequest;
import cn.langchat.aigc.biz.entity.AigcAgent;
import cn.langchat.aigc.biz.service.AigcAgentService;
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
 * Agent 管理控制器。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@RestController
@RequestMapping("/api/v1/aigc/agents")
@RequiredArgsConstructor
@Slf4j
@SaCheckPermission("aigc:agent:list")
public class AigcAgentController {

    private final AigcAgentService aigcAgentService;

    /**
     * 查询 Agent 全量列表（保留给下拉框/关联选择使用）。
     */
    @GetMapping
    public ApiResponse<List<AigcAgent>> list() {
        return ApiResponse.success(aigcAgentService.lambdaQuery()
                .orderByDesc(AigcAgent::getCreateTime)
                .list());
    }

    /**
     * 分页查询 Agent 列表。
     *
     * @param pageNo 页码，默认 1
     * @param pageSize 页大小，默认 20、上限 200
     * @param keyword 关键字（Agent 名称 / 描述模糊匹配）
     * @param status Agent 状态（精确匹配）
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<AigcAgent>> page(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status
    ) {
        PageQuery page = PageQuery.of(pageNo, pageSize);
        LambdaQueryWrapper<AigcAgent> wrapper = Wrappers.<AigcAgent>lambdaQuery()
                .eq(StringUtils.hasText(status), AigcAgent::getStatus, status)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(AigcAgent::getAgentName, keyword)
                        .or().like(AigcAgent::getDescription, keyword))
                .orderByDesc(AigcAgent::getCreateTime);
        Page<AigcAgent> result = aigcAgentService.page(
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
     * 查询 Agent 详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<AigcAgent> detail(@PathVariable("id") String id) {
        return ApiResponse.success(aigcAgentService.getById(id));
    }

    /**
     * 新增 Agent。
     */
    @PostMapping
    @AuditLog(type = AuditLogType.AGENT, action = "新增 Agent")
    public ApiResponse<Boolean> create(@Valid @RequestBody AgentSaveRequest request) {
        AigcAgent agent = new AigcAgent();
        BeanUtils.copyProperties(request, agent);
        log.info("新增 Agent，agentName={}", agent.getAgentName());
        return ApiResponse.success(aigcAgentService.save(agent));
    }

    /**
     * 更新 Agent。
     */
    @PutMapping("/{id}")
    @AuditLog(type = AuditLogType.AGENT, action = "更新 Agent")
    public ApiResponse<Boolean> update(
            @PathVariable("id") String id,
            @Valid @RequestBody AgentSaveRequest request
    ) {
        AigcAgent agent = new AigcAgent();
        BeanUtils.copyProperties(request, agent);
        agent.setId(id);
        log.info("更新 Agent，id={}", id);
        return ApiResponse.success(aigcAgentService.updateById(agent));
    }

    /**
     * 删除 Agent。
     */
    @DeleteMapping("/{id}")
    @AuditLog(type = AuditLogType.AGENT, action = "删除 Agent")
    public ApiResponse<Boolean> remove(@PathVariable("id") String id) {
        log.info("删除 Agent，id={}", id);
        return ApiResponse.success(aigcAgentService.removeById(id));
    }
}
