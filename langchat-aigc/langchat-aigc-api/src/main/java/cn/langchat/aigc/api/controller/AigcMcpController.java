package cn.langchat.aigc.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.langchat.aigc.api.model.request.McpSaveRequest;
import cn.langchat.aigc.biz.entity.AigcMcp;
import cn.langchat.aigc.biz.event.McpConfigChangedEvent;
import cn.langchat.aigc.biz.service.AigcMcpService;
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
import org.springframework.context.ApplicationEventPublisher;
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
 * MCP 管理控制器。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@RestController
@RequestMapping("/api/v1/aigc/mcp")
@RequiredArgsConstructor
@Slf4j
@SaCheckPermission("aigc:mcp:list")
public class AigcMcpController {

    private final AigcMcpService aigcMcpService;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping
    public ApiResponse<List<AigcMcp>> list() {
        return ApiResponse.success(aigcMcpService.lambdaQuery().orderByDesc(AigcMcp::getCreateTime).list());
    }

    /**
     * 分页查询 MCP 配置列表（服务端分页）。
     *
     * @param pageNo 页码，默认 1
     * @param pageSize 页大小，默认 20、上限 200
     * @param keyword 关键字（服务名称模糊匹配）
     * @param tags 标签（模糊匹配，前端标签快捷筛选）
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<AigcMcp>> page(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "tags", required = false) String tags
    ) {
        PageQuery page = PageQuery.of(pageNo, pageSize);
        LambdaQueryWrapper<AigcMcp> wrapper = Wrappers.<AigcMcp>lambdaQuery()
                .like(StringUtils.hasText(keyword), AigcMcp::getName, keyword)
                .like(StringUtils.hasText(tags), AigcMcp::getTags, tags)
                .orderByDesc(AigcMcp::getCreateTime);
        Page<AigcMcp> result = aigcMcpService.page(
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
    public ApiResponse<AigcMcp> detail(@PathVariable("id") String id) {
        return ApiResponse.success(aigcMcpService.getById(id));
    }

    @PostMapping
    @AuditLog(type = AuditLogType.MCP, action = "新增MCP配置")
    public ApiResponse<Boolean> create(@Valid @RequestBody McpSaveRequest request) {
        AigcMcp mcp = new AigcMcp();
        BeanUtils.copyProperties(request, mcp);
        log.info("新增MCP配置，name={}", mcp.getName());
        return ApiResponse.success(aigcMcpService.save(mcp));
    }

    @PutMapping("/{id}")
    @AuditLog(type = AuditLogType.MCP, action = "更新MCP配置")
    public ApiResponse<Boolean> update(@PathVariable("id") String id, @Valid @RequestBody McpSaveRequest request) {
        AigcMcp mcp = new AigcMcp();
        BeanUtils.copyProperties(request, mcp);
        mcp.setId(id);
        log.info("更新MCP配置，id={}", id);
        boolean updated = aigcMcpService.updateById(mcp);
        // 通知运行时立即停用旧连接（主动暂停），下次调用按新配置重建
        eventPublisher.publishEvent(new McpConfigChangedEvent(id));
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/{id}")
    @AuditLog(type = AuditLogType.MCP, action = "删除MCP配置")
    public ApiResponse<Boolean> remove(@PathVariable("id") String id) {
        log.info("删除MCP配置，id={}", id);
        boolean removed = aigcMcpService.removeById(id);
        eventPublisher.publishEvent(new McpConfigChangedEvent(id));
        return ApiResponse.success(removed);
    }
}
