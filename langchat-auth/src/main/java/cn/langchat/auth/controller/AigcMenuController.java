package cn.langchat.auth.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.langchat.auth.entity.AigcMenu;
import cn.langchat.auth.model.response.AigcMenuTreeNode;
import cn.langchat.auth.service.AigcMenuService;
import cn.langchat.common.core.ApiResponse;
import cn.langchat.common.core.page.PageQuery;
import cn.langchat.common.core.page.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 菜单管理控制器。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@RestController
@RequestMapping("/api/v1/auth/menus")
@RequiredArgsConstructor
@Slf4j
@SaCheckPermission("langchat:menu:list")
public class AigcMenuController {

    private final AigcMenuService aigcMenuService;

    @GetMapping
    public ApiResponse<List<AigcMenu>> list() {
        return ApiResponse.success(aigcMenuService.lambdaQuery().orderByAsc(AigcMenu::getOrderNo).list());
    }

    /**
     * 分页查询菜单（按排序号升序）。
     *
     * @param pageNo 页码，默认 1
     * @param pageSize 页大小，默认 20、上限 200
     * @param keyword 关键字（菜单名称 / 路由路径模糊匹配）
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<AigcMenu>> page(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        PageQuery pageQuery = PageQuery.of(pageNo, pageSize);
        LambdaQueryWrapper<AigcMenu> wrapper = Wrappers.<AigcMenu>lambdaQuery()
                .and(StringUtils.hasText(keyword), query -> query
                        .like(AigcMenu::getName, keyword)
                        .or().like(AigcMenu::getPath, keyword))
                .orderByAsc(AigcMenu::getOrderNo)
                .orderByAsc(AigcMenu::getCreateTime);
        Page<AigcMenu> page = aigcMenuService.page(
                new Page<>(pageQuery.getPageNo(), pageQuery.getPageSize()), wrapper);
        return ApiResponse.success(PageResult.of(
                page.getRecords(), page.getTotal(), pageQuery.getPageNo(), pageQuery.getPageSize()));
    }

    /**
     * 查询树形菜单。
     */
    @GetMapping("/tree")
    public ApiResponse<List<AigcMenuTreeNode>> listTree(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "type", required = false) String type
    ) {
        return ApiResponse.success(aigcMenuService.listTree(keyword, type));
    }

    @GetMapping("/{id}")
    public ApiResponse<AigcMenu> detail(@PathVariable("id") String id) {
        return ApiResponse.success(aigcMenuService.getById(id));
    }

    @PostMapping
    public ApiResponse<Boolean> create(@RequestBody AigcMenu menu) {
        log.info("新增菜单，name={}", menu.getName());
        return ApiResponse.success(aigcMenuService.save(menu));
    }

    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable("id") String id, @RequestBody AigcMenu menu) {
        menu.setId(id);
        log.info("更新菜单，id={}", id);
        return ApiResponse.success(aigcMenuService.updateById(menu));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> remove(@PathVariable("id") String id) {
        log.info("删除菜单，id={}", id);
        return ApiResponse.success(aigcMenuService.removeById(id));
    }
}
