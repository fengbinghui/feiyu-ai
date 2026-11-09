package cn.langchat.datasource.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.langchat.common.core.ApiResponse;
import cn.langchat.common.core.audit.AuditLog;
import cn.langchat.common.core.audit.AuditLogType;
import cn.langchat.common.core.page.PageQuery;
import cn.langchat.common.core.page.PageResult;
import cn.langchat.datasource.entity.AigcDatasource;
import cn.langchat.datasource.model.DatabaseStructure;
import cn.langchat.datasource.model.DatasourceSaveRequest;
import cn.langchat.datasource.model.SaveStructureRequest;
import cn.langchat.datasource.model.TestConnectionRequest;
import cn.langchat.datasource.service.AigcDatasourceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * 数据源管理控制器。
 *
 * @author LangChat Team
 * @since 2026/8/27
 */
@RestController
@RequestMapping("/api/v1/datasources")
@RequiredArgsConstructor
@Slf4j
@SaCheckPermission("aigc:datasource:list")
public class AigcDatasourceController {

    private final AigcDatasourceService aigcDatasourceService;

    @GetMapping
    public ApiResponse<List<AigcDatasource>> list() {
        List<AigcDatasource> datasources = aigcDatasourceService.lambdaQuery()
                .orderByDesc(AigcDatasource::getCreateTime)
                .list();
        // 敏感字段脱敏：接口不返回完整数据库密码。
        datasources.forEach(datasource -> datasource.setPassword(maskSecret(datasource.getPassword())));
        return ApiResponse.success(datasources);
    }

    /**
     * 分页查询数据源列表（服务端分页）。
     *
     * @param pageNo 页码，默认 1
     * @param pageSize 页大小，默认 20、上限 200
     * @param keyword 关键字（数据源名称 / 备注模糊匹配；实体无 description 列，备注为最接近字段）
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<AigcDatasource>> page(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        PageQuery page = PageQuery.of(pageNo, pageSize);
        LambdaQueryWrapper<AigcDatasource> wrapper = Wrappers.<AigcDatasource>lambdaQuery()
                .and(StringUtils.hasText(keyword), query -> query
                        .like(AigcDatasource::getName, keyword)
                        .or().like(AigcDatasource::getRemark, keyword))
                .orderByDesc(AigcDatasource::getCreateTime);
        Page<AigcDatasource> result = aigcDatasourceService.page(
                new Page<>(page.getPageNo(), page.getPageSize()),
                wrapper
        );
        // 敏感字段脱敏：分页结果同样不返回完整数据库密码。
        result.getRecords().forEach(datasource -> datasource.setPassword(maskSecret(datasource.getPassword())));
        return ApiResponse.success(PageResult.of(
                result.getRecords(),
                result.getTotal(),
                page.getPageNo(),
                page.getPageSize()
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<AigcDatasource> detail(@PathVariable("id") String id) {
        AigcDatasource datasource = aigcDatasourceService.getById(id);
        if (datasource != null) {
            datasource.setPassword(maskSecret(datasource.getPassword()));
        }
        return ApiResponse.success(datasource);
    }

    @PostMapping
    @AuditLog(type = AuditLogType.DATASOURCE, action = "新增数据源")
    public ApiResponse<Boolean> create(@Valid @RequestBody DatasourceSaveRequest request) {
        AigcDatasource datasource = new AigcDatasource();
        BeanUtils.copyProperties(request, datasource);
        return ApiResponse.success(aigcDatasourceService.save(datasource));
    }

    @PutMapping("/{id}")
    @AuditLog(type = AuditLogType.DATASOURCE, action = "更新数据源")
    public ApiResponse<Boolean> update(
            @PathVariable("id") String id,
            @Valid @RequestBody DatasourceSaveRequest request
    ) {
        AigcDatasource datasource = new AigcDatasource();
        BeanUtils.copyProperties(request, datasource);
        datasource.setId(id);
        // 前端回显的是脱敏后的密码（含 *）或未传，此时保留数据库中的原始密码。
        if (isMaskedOrBlank(datasource.getPassword())) {
            AigcDatasource existing = aigcDatasourceService.getById(id);
            datasource.setPassword(existing == null ? null : existing.getPassword());
        }
        return ApiResponse.success(aigcDatasourceService.updateById(datasource));
    }

    @DeleteMapping("/{id}")
    @AuditLog(type = AuditLogType.DATASOURCE, action = "删除数据源")
    public ApiResponse<Boolean> remove(@PathVariable("id") String id) {
        return ApiResponse.success(aigcDatasourceService.removeById(id));
    }

    /**
     * 测试连接。
     */
    @PostMapping("/test")
    @AuditLog(type = AuditLogType.DATASOURCE, action = "测试数据源连接")
    public ApiResponse<Boolean> test(@RequestBody TestConnectionRequest request) {
        return ApiResponse.success(aigcDatasourceService.testConnection(request));
    }

    /**
     * 实时内省表结构。
     */
    @GetMapping("/{id}/introspect")
    public ApiResponse<DatabaseStructure> introspect(@PathVariable("id") String id) throws Exception {
        return ApiResponse.success(aigcDatasourceService.introspect(id));
    }

    /**
     * 获取自定义表结构 JSON。
     */
    @GetMapping("/{id}/structure")
    public ApiResponse<Map<String, String>> getStructure(@PathVariable("id") String id) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("structureJson", aigcDatasourceService.getStructure(id));
        return ApiResponse.success(result);
    }

    /**
     * 保存自定义表结构 JSON。
     */
    @PutMapping("/{id}/structure")
    public ApiResponse<Boolean> saveStructure(@PathVariable("id") String id, @RequestBody SaveStructureRequest request) {
        return ApiResponse.success(aigcDatasourceService.saveStructure(id, request.structureJson()));
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
