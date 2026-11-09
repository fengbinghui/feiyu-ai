package cn.langchat.aigc.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.langchat.aigc.api.model.request.SkillSaveRequest;
import cn.langchat.aigc.biz.entity.AigcSkill;
import cn.langchat.aigc.biz.service.AigcSkillService;
import cn.langchat.aigc.biz.service.SkillPackageService;
import cn.langchat.aigc.biz.service.skill.SkillFileContent;
import cn.langchat.common.exception.BizException;
import cn.langchat.aigc.biz.service.skill.SkillFileNode;
import cn.langchat.common.core.ApiResponse;
import cn.langchat.common.core.audit.AuditLog;
import cn.langchat.common.core.audit.AuditLogType;
import cn.langchat.common.core.page.PageQuery;
import cn.langchat.common.core.page.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * 技能包管理接口。技能以标准化技能包（根目录含 SKILL.md）形式管理，
 * 原始包归档 OSS，本地工作区存放解压副本供运行时直接调用。
 *
 * @author LangChat Team
 * @since 2026/3/24
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/aigc/skills")
@SaCheckPermission("aigc:skill:list")
public class AigcSkillController {

    /** 技能 zip 包上传上限：50 MB。 */
    private static final long MAX_PACKAGE_SIZE = 50L * 1024 * 1024;
    /** 技能文件夹上传时单个文件上限：10 MB。 */
    private static final long MAX_FOLDER_FILE_SIZE = 10L * 1024 * 1024;

    private final AigcSkillService aigcSkillService;
    private final SkillPackageService skillPackageService;

    /**
     * 技能列表。
     */
    @GetMapping
    public ApiResponse<List<AigcSkill>> list() {
        return ApiResponse.success(aigcSkillService.lambdaQuery()
                .orderByDesc(AigcSkill::getCreateTime)
                .list());
    }

    /**
     * 分页查询技能列表（服务端分页）。
     *
     * @param pageNo 页码，默认 1
     * @param pageSize 页大小，默认 20、上限 200
     * @param keyword 关键字（技能标识 / 描述模糊匹配）
     * @param tags 标签（模糊匹配，前端标签快捷筛选）
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<AigcSkill>> page(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "tags", required = false) String tags
    ) {
        PageQuery page = PageQuery.of(pageNo, pageSize);
        LambdaQueryWrapper<AigcSkill> wrapper = Wrappers.<AigcSkill>lambdaQuery()
                .and(StringUtils.hasText(keyword), query -> query
                        .like(AigcSkill::getName, keyword)
                        .or().like(AigcSkill::getDescription, keyword))
                .like(StringUtils.hasText(tags), AigcSkill::getTags, tags)
                .orderByDesc(AigcSkill::getCreateTime);
        Page<AigcSkill> result = aigcSkillService.page(
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
     * 技能详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<AigcSkill> detail(@PathVariable("id") String id) {
        return ApiResponse.success(aigcSkillService.getById(id));
    }

    /**
     * 更新技能元数据（标签、启用状态）。
     *
     * <p>前端既有调用为局部更新（如启停开关只提交 enabled），
     * 因此 {@link SkillSaveRequest} 不设置必填校验，未提交的字段保持数据库原值。</p>
     */
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable("id") String id, @Valid @RequestBody SkillSaveRequest request) {
        AigcSkill skill = requireSkill(id);
        BeanUtils.copyProperties(request, skill);
        // 保持原有语义：未提交 enabled 时视为启用。
        skill.setEnabled(request.getEnabled() == null || request.getEnabled());
        return ApiResponse.success(aigcSkillService.updateById(skill));
    }

    /**
     * 删除技能（本地工作区 + OSS 原始包 + 记录）。
     */
    @DeleteMapping("/{id}")
    @AuditLog(type = AuditLogType.SKILL, action = "删除技能")
    public ApiResponse<Boolean> delete(@PathVariable("id") String id) {
        AigcSkill skill = requireSkill(id);
        skillPackageService.removePackage(skill);
        return ApiResponse.success(aigcSkillService.removeById(id));
    }

    /**
     * 上传 zip 技能包。
     */
    @PostMapping(value = "/upload/package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AuditLog(type = AuditLogType.SKILL, action = "上传技能包")
    public ApiResponse<AigcSkill> uploadPackage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tags", required = false) String tags) throws IOException {
        validatePackageUpload(file);
        String filename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "skill-package.zip";
        AigcSkill skill = skillPackageService.installPackage(file.getBytes(), filename, tags);
        return ApiResponse.success(skill);
    }

    /**
     * 上传文件夹技能包：files 与 paths 一一对应，paths 为浏览器提供的相对路径。
     */
    @PostMapping(value = "/upload/folder", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AuditLog(type = AuditLogType.SKILL, action = "上传技能文件夹")
    public ApiResponse<AigcSkill> uploadFolder(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("paths") List<String> paths,
            @RequestParam(value = "tags", required = false) String tags) throws IOException {
        if (files.size() != paths.size()) {
            throw new BizException("SKILL_FOLDER_MISMATCH", "文件数量与路径数量不一致");
        }
        Map<String, byte[]> fileMap = new HashMap<>();
        String rootName = null;
        for (int index = 0; index < files.size(); index++) {
            String path = paths.get(index);
            if (!StringUtils.hasText(path)) {
                continue;
            }
            MultipartFile uploadFile = files.get(index);
            validateFolderUpload(uploadFile);
            fileMap.put(path, uploadFile.getBytes());
            String normalized = path.replace('\\', '/');
            int separator = normalized.indexOf('/');
            if (separator > 0 && rootName == null) {
                rootName = normalized.substring(0, separator);
            }
        }
        AigcSkill skill = skillPackageService.installFolder(fileMap, rootName, tags);
        return ApiResponse.success(skill);
    }

    /**
     * 技能包文件树。
     */
    @GetMapping("/{id}/files")
    public ApiResponse<List<SkillFileNode>> files(@PathVariable("id") String id) {
        return ApiResponse.success(skillPackageService.listFiles(requireSkill(id)));
    }

    /**
     * 读取技能包内文件内容。
     */
    @GetMapping("/{id}/file")
    public ApiResponse<SkillFileContent> readFile(
            @PathVariable("id") String id,
            @RequestParam("path") String path) {
        return ApiResponse.success(skillPackageService.readFile(requireSkill(id), path));
    }

    /**
     * 保存技能包内文件内容（SKILL.md 会同步 frontmatter 元数据）。
     */
    @PutMapping("/{id}/file")
    public ApiResponse<AigcSkill> saveFile(
            @PathVariable("id") String id,
            @RequestBody Map<String, String> payload) {
        AigcSkill skill = requireSkill(id);
        String path = payload.getOrDefault("path", "");
        String content = payload.getOrDefault("content", "");
        AigcSkill updated = skillPackageService.saveFile(skill, path, content);
        aigcSkillService.updateById(updated);
        return ApiResponse.success(updated);
    }

    /**
     * 下载原始技能包（从 OSS）。
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable("id") String id) {
        AigcSkill skill = requireSkill(id);
        byte[] bytes = skillPackageService.downloadPackage(skill);
        String filename = StringUtils.hasText(skill.getOssFilename()) ? skill.getOssFilename() : "skill-package.zip";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                        .attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(bytes);
    }

    private AigcSkill requireSkill(String id) {
        AigcSkill skill = aigcSkillService.getById(id);
        if (skill == null) {
            throw new BizException("SKILL_NOT_FOUND", "技能不存在");
        }
        return skill;
    }

    /**
     * 校验技能 zip 包上传：仅允许 .zip 且不超过 50 MB。
     */
    private void validatePackageUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("SKILL_UPLOAD_INVALID", "上传文件不能为空");
        }
        if (file.getSize() > MAX_PACKAGE_SIZE) {
            throw new BizException("SKILL_UPLOAD_INVALID", "技能包不能超过 50 MB");
        }
        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip")) {
            throw new BizException("SKILL_UPLOAD_INVALID", "技能包仅支持 .zip 格式");
        }
    }

    /**
     * 校验技能文件夹上传：单个文件不超过 10 MB，且路径非空。
     */
    private void validateFolderUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("SKILL_UPLOAD_INVALID", "上传文件不能为空");
        }
        if (file.getSize() > MAX_FOLDER_FILE_SIZE) {
            throw new BizException("SKILL_UPLOAD_INVALID", "文件夹上传时单个文件不能超过 10 MB");
        }
    }
}
