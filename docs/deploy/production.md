# 肥鱼AI（LangChat）生产部署清单

本文档描述后端（`langchat-server`）在生产环境部署时需要关注的环境变量、数据库迁移、安全与运维配置。
本地开发环境参考 `application-local.example.yml`，生产参考 `application-prod.yml`。

## 1. 基础环境

| 组件 | 版本/要求 | 说明 |
|---|---|---|
| JDK | 17 | 与 Spring Boot 3.3.5 匹配 |
| MySQL | 8.0+ | 主库（业务数据 + Flyway 历史表） |
| Pgvector / PostgreSQL | 可选 | 知识库向量检索（通过「向量库」配置接入，可替换 Milvus） |
| 前端 | Node 20/22 构建产物 | 静态托管，或由 Nginx 反代到后端 8081 |

构建命令（Windows）：

```powershell
$env:JAVA_HOME='E:\java\jdk17'
& 'E:\maven\apache-maven-3.8.1\bin\mvn.cmd' -B -pl langchat-server -am -DskipTests package
```

产物：`langchat-server/target/langchat-server.jar`，启动：

```bash
java -jar langchat-server.jar --spring.profiles.active=prod
```

## 2. 必填环境变量

| 变量 | 必填 | 说明 |
|---|---|---|
| `LANGCHAT_MYSQL_URL` | 是 | 例：`jdbc:mysql://127.0.0.1:3306/langchat?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false` |
| `LANGCHAT_MYSQL_USERNAME` | 是 | 数据库账号（最小权限原则，建议仅授予业务库 DML + Flyway DDL） |
| `LANGCHAT_MYSQL_PASSWORD` | 是 | 数据库密码，使用密钥管理系统注入，禁止写入仓库 |
| `LANGCHAT_CORS_ALLOWED_ORIGINS` | 是（prod） | 允许跨域的前端来源，逗号分隔，如 `https://ai.example.com` |
| `LANGCHAT_ADMIN_USERNAME` | 是 | 超管用户名（默认 admin 需按需修改） |
| `LANGCHAT_ADMIN_PASSWORD` | 建议 | 超管兜底密码（生产建议配合 BCrypt 数据库账号使用） |
| `LANGCHAT_STORAGE_TYPE` | 否 | `local` 或 `s3`（对话附件/知识库原始文件） |
| `LANGCHAT_LOCAL_STORAGE_DIR` | local 必填 | 本地存储目录，如 `/opt/langchat/oss` |
| `AMAZON_S3_*` | s3 必填 | `AMAZON_S3_ACCESS_KEY` / `AMAZON_S3_SECRET_KEY` / `AMAZON_S3_BUCKET_NAME` / `AMAZON_S3_END_POINT` / `AMAZON_S3_REGION` |
| `LANGCHAT_SKILL_WORKSPACE_DIR` | 否 | 技能工作区目录（需持久化） |

## 3. 数据库迁移（Flyway）

- 依赖：`flyway-core` + `flyway-mysql`（版本由 Spring Boot BOM 管理，当前 10.x）。
- 配置位于 `application.yml` 的 `spring.flyway`：
  - `enabled=${LANGCHAT_FLYWAY_ENABLED:true}`
  - `baseline-on-migrate=true`、`baseline-version=1`：**已有库首次接入 Flyway 时，会把 V1 基线标记为已应用**，不重复执行建表。
  - `locations=classpath:db/migration`、`table=flyway_schema_history`
- 迁移脚本：
  - `V1__baseline_schema.sql`：全库建表（`CREATE TABLE IF NOT EXISTS`）+ 种子数据（`INSERT IGNORE`），用于全新环境一次性初始化。
  - `V2__audit_log_enhance.sql`：`aigc_log.params` 扩展为 `TEXT`，并新增 `create_time/type/username` 索引。
- 首次上线注意事项：
  1. 备份数据库；
  2. 启动日志中确认 `Flyway` 执行成功（出现 `Successfully applied ... migration`、`Schema ... is up to date` 等日志）；
  3. 现有库首次启动后检查 `flyway_schema_history` 表：V1 为 baseline、V2 为已应用；
  4. 回滚：Flyway 不做自动回滚，异常时使用备份恢复 + 手工修正 `flyway_schema_history`。
- 紧急跳过迁移（不推荐）：`LANGCHAT_FLYWAY_ENABLED=false`。

## 4. 安全配置

### 4.1 关闭接口文档（生产）

```bash
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
```

`application-prod.yml` 默认已关闭；仅在受控内网临时排查时开启，并通过网关做 IP 白名单。

### 4.2 CORS

- 必须显式配置 `LANGCHAT_CORS_ALLOWED_ORIGINS`，禁止使用 `*`（当前实现 `allowCredentials=true`，通配不安全）。
- 变更前端域名后需重启后端生效。

### 4.3 密钥管理

- 禁止把数据库密码、S3 AccessKey/SecretKey、模型 API Key 写入 `application*.yml`、镜像或 Git 仓库。
- 推荐做法：K8s Secret / Vault / 云厂商密钥管理服务注入环境变量；应用侧仅读取环境变量。
- 模型 API Key 通过「模型管理」界面配置，落库前已脱敏展示（列表/详情接口返回 `sk-****xxxx`），更新时传空或含 `*` 会保留原值。

### 4.4 认证与权限

- 除登录、注册、公开分享等白名单外，所有 `/api/**` 需登录；Controller 标注 `@SaCheckPermission` 做 RBAC。
- 登录失败风控默认 5 次失败锁定 10 分钟（`langchat.auth.login-*`）。
- 生产建议开启 HTTPS，并在网关层限制上传大小与频率。

## 5. 审计日志

- 审计日志写入 `aigc_log`，采用异步线程池（core=1/max=4/queue=1000，CallerRunsPolicy），写入失败不阻断业务。
- 保留策略配置（`application.yml` / 环境变量）：

```yaml
langchat:
  audit:
    retention-days: ${LANGCHAT_AUDIT_RETENTION_DAYS:90}
    retention-cron: ${LANGCHAT_AUDIT_RETENTION_CRON:0 30 3 * * ?}
```

- 清理任务每天 03:30 删除 `create_time` 早于保留天数的记录；如需合规长期留存，请先归档到日志平台再缩短保留天数。
- 运维可通过只读接口 `GET /api/v1/aigc/logs`（权限 `langchat:log:list`）分页查询，接口不支持修改/删除日志。

## 6. 监控与健康检查

- 健康检查：`GET /actuator/health`（生产不暴露明细：`management.endpoint.health.show-details=never`）。
- 必看指标：接口 5xx、登录失败率（429）、模型调用失败率（`/api/v1/monitor/model-calls/overview`）、审计线程池队列（日志 `langchat-audit-*`）。
- 建议日志采集 stdout（logback 已配置），并保留 `requestId` 用于排障。

## 7. 升级步骤（含 Flyway）

1. 备份 MySQL 与对象存储；
2. 构建新 jar 并分发；
3. 停旧实例（滚动发布注意单实例 Flyway 由首个实例执行，其余实例等待 `flyway_schema_history` 完成）；
4. 启动新实例，观察日志中 Flyway 迁移结果与启动异常；
5. 健康检查通过后切换流量；
6. 验证关键链路：登录、对话 SSE、知识库 RAG、审计日志分页、用量统计。

## 8. 常见问题

| 现象 | 排查方向 |
|---|---|
| 启动报 `Found non-empty schema but no schema history table` | 确认 `baseline-on-migrate=true`、`baseline-version=1`，且未手动指定 `spring.flyway.url` |
| Flyway 报 MySQL 不支持 | 缺少 `flyway-mysql` 依赖或数据库版本低于 8.0 |
| 审计日志查不到 | 确认 `aigc_log.params` 已由 V2 迁移为 TEXT；检查日志中 `langchat-audit-*` 线程报错 |
| Swagger 404 | 生产默认关闭，检查 `SPRINGDOC_*` 环境变量 |
| CORS 报错 | 检查 `LANGCHAT_CORS_ALLOWED_ORIGINS` 是否包含完整协议+域名+端口 |
