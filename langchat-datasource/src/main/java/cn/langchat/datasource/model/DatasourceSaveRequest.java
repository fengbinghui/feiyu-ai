package cn.langchat.datasource.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 数据源新增/更新请求对象。
 *
 * <p>字段与 {@code cn.langchat.datasource.entity.AigcDatasource} 的业务字段一一对应
 * （不含 id 与 creator/updater/createTime/updateTime 审计字段），
 * 前端现有 JSON 请求体无需改动。{@code url} 由服务端根据连接信息自动生成，
 * {@code structureJson} 为 longtext 列，均不限制长度。</p>
 *
 * @author LangChat Team
 * @since 2026/11/9
 */
@Data
public class DatasourceSaveRequest {

    /** 数据源名称。 */
    @NotBlank(message = "数据源名称不能为空")
    @Size(max = 100, message = "数据源名称长度不能超过100位")
    private String name;

    /** 数据库类型：MYSQL / POSTGRESQL / ORACLE / SQLSERVER。 */
    @NotBlank(message = "数据库类型不能为空")
    @Size(max = 50, message = "数据库类型长度不能超过50位")
    private String dbType;

    /** 主机地址。 */
    @NotBlank(message = "主机地址不能为空")
    @Size(max = 100, message = "主机地址长度不能超过100位")
    private String host;

    /** 端口。 */
    @NotNull(message = "端口不能为空")
    private Integer port;

    /** 数据库名。 */
    @NotBlank(message = "数据库名不能为空")
    @Size(max = 100, message = "数据库名长度不能超过100位")
    private String databaseName;

    /** 连接 URL（由服务端根据数据库类型、主机、端口和库名自动生成）。 */
    @Size(max = 500, message = "连接URL长度不能超过500位")
    private String url;

    /** 用户名。 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 100, message = "用户名长度不能超过100位")
    private String username;

    /** 密码。 */
    @NotBlank(message = "密码不能为空")
    @Size(max = 255, message = "密码长度不能超过255位")
    private String password;

    /** Schema 名称（Oracle / PostgreSQL 用）。 */
    @Size(max = 100, message = "Schema 名称长度不能超过100位")
    private String schemaName;

    /** 自定义表结构 JSON（longtext 列，不限制长度）。 */
    private String structureJson;

    /** 备注。 */
    @Size(max = 500, message = "备注长度不能超过500位")
    private String remark;

    /** 是否启用。 */
    private Boolean enabled;
}
