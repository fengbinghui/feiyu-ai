package cn.langchat.auth.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.langchat.auth.entity.AigcRole;
import cn.langchat.auth.model.request.AigcRoleSaveRequest;
import cn.langchat.auth.service.AigcRoleService;
import cn.langchat.common.core.ApiResponse;
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
 * 角色管理控制器。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@RestController
@RequestMapping("/api/v1/auth/roles")
@RequiredArgsConstructor
@Slf4j
@SaCheckPermission("langchat:role:list")
public class AigcRoleController {

    private final AigcRoleService aigcRoleService;

    @GetMapping
    public ApiResponse<List<AigcRole>> list() {
        return ApiResponse.success(aigcRoleService.lambdaQuery().orderByDesc(AigcRole::getCreateTime).list());
    }

    /**
     * 分页查询角色。
     *
     * @param pageNo 页码，默认 1
     * @param pageSize 页大小，默认 20、上限 200
     * @param keyword 关键字（角色名称 / 角色编码模糊匹配）
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<AigcRole>> page(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        PageQuery pageQuery = PageQuery.of(pageNo, pageSize);
        LambdaQueryWrapper<AigcRole> wrapper = Wrappers.<AigcRole>lambdaQuery()
                .and(StringUtils.hasText(keyword), query -> query
                        .like(AigcRole::getName, keyword)
                        .or().like(AigcRole::getCode, keyword))
                .orderByDesc(AigcRole::getCreateTime);
        Page<AigcRole> page = aigcRoleService.page(
                new Page<>(pageQuery.getPageNo(), pageQuery.getPageSize()), wrapper);
        return ApiResponse.success(PageResult.of(
                page.getRecords(), page.getTotal(), pageQuery.getPageNo(), pageQuery.getPageSize()));
    }

    @GetMapping("/{id}")
    public ApiResponse<AigcRole> detail(@PathVariable("id") String id) {
        return ApiResponse.success(aigcRoleService.getById(id));
    }

    @PostMapping
    public ApiResponse<Boolean> create(@Valid @RequestBody AigcRoleSaveRequest request) {
        log.info("新增角色，code={}", request.getCode());
        AigcRole role = new AigcRole();
        BeanUtils.copyProperties(request, role);
        return ApiResponse.success(aigcRoleService.save(role));
    }

    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(
            @PathVariable("id") String id,
            @Valid @RequestBody AigcRoleSaveRequest request
    ) {
        log.info("更新角色，id={}", id);
        AigcRole role = new AigcRole();
        BeanUtils.copyProperties(request, role);
        role.setId(id);
        return ApiResponse.success(aigcRoleService.updateById(role));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> remove(@PathVariable("id") String id) {
        log.info("删除角色，id={}", id);
        return ApiResponse.success(aigcRoleService.removeById(id));
    }
}
