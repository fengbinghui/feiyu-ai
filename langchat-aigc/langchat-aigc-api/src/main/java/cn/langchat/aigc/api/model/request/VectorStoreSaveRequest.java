package cn.langchat.aigc.api.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 向量库新增/更新请求对象。
 *
 * <p>字段与 {@code cn.langchat.aigc.biz.entity.AigcVectorStore} 的业务字段一一对应
 * （不含 id 与 creator/updater/createTime/updateTime 审计字段），
 * 前端现有 JSON 请求体无需改动。表名/集合名是否必填依赖供应商，
 * 由控制器中的 {@code validateVectorStore} 继续做条件校验。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Data
public class VectorStoreSaveRequest {

    /** 向量库名称。 */
    @NotBlank(message = "向量库名称不能为空")
    @Size(max = 100, message = "向量库名称长度不能超过100位")
    private String name;

    /** 向量库供应商。 */
    @NotBlank(message = "向量库供应商不能为空")
    @Size(max = 100, message = "向量库供应商长度不能超过100位")
    private String provider;

    /** 主机地址。 */
    @NotBlank(message = "向量库主机地址不能为空")
    @Size(max = 100, message = "主机地址长度不能超过100位")
    private String host;

    /** 端口。 */
    private Integer port;

    /** 用户名。 */
    @Size(max = 100, message = "用户名长度不能超过100位")
    private String username;

    /** 密码。 */
    @Size(max = 100, message = "密码长度不能超过100位")
    private String password;

    /** 数据库名称。 */
    @Size(max = 100, message = "数据库名称长度不能超过100位")
    private String databaseName;

    /** 向量维度。 */
    @NotNull(message = "向量维度不能为空")
    private Integer dimension;

    /** 表名或集合名。 */
    @Size(max = 255, message = "表名/集合名长度不能超过255位")
    private String tableName;
}
