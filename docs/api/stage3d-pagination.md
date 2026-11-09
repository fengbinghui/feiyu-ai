# 阶段3d 接口契约：服务端分页 + 请求 DTO 校验

> 本文件是阶段3d前后端并行开发的唯一契约。后端按此实现，前端按此对接；未覆盖的细节在实现中就近注释，不得单方面改动契约。

## 一、服务端分页

### 1.1 路径与参数

- 路径：`GET {basePath}/page`
- 查询参数（全部可选）：

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `pageNo` | int | 1 | 页码，<1 归一化为 1 |
| `pageSize` | int | 20 | 页大小，上限 200（超过按 200） |
| `keyword` | string | - | 模糊搜索，匹配列见下表 |
| 其余 | string/int | - | 各资源过滤参数见下表 |

- 排序：默认 `create_time desc`（菜单按 `sort` 升序，见任务说明）。
- 权限：与该控制器原有 `GET {basePath}` 完全一致（类级 `@SaCheckPermission` 不变）。
- **原有 `GET {basePath}` 全量列表接口保持不变**（下拉框、关联选择仍用它），不破坏既有前端调用。

### 1.2 响应结构

`ApiResponse<PageResult<T>>`，前端拦截器解包后拿到：

```json
{
  "records": [ { "id": "...", "..." : "..." } ],
  "total": 37,
  "pageNo": 2,
  "pageSize": 10
}
```

复用 `cn.langchat.common.core.page.PageResult` / `PageQuery`（已存在于 langchat-common-core）。

### 1.3 资源清单

| 资源 | basePath | keyword 匹配列 | 额外过滤参数 |
| --- | --- | --- | --- |
| 用户 | `/api/v1/auth/users` | username / realName / phone / email | `status`(int) |
| 角色 | `/api/v1/auth/roles` | name / code | - |
| 菜单 | `/api/v1/auth/menus` | name / path | - |
| Agent | `/api/v1/aigc/agents` | agentName / description | `status`(string) |
| 知识库 | `/api/v1/aigc/knowledges` | name / description | - |
| 文档 | `/api/v1/aigc/docs` | name | `knowledgeId`(string)、`embedStatus`(string) |
| 模型 | `/api/v1/aigc/models` | modelName / modelShowName | `modelType`(string) |
| 技能 | `/api/v1/aigc/skills` | name / description | `tags`(string) |
| MCP | `/api/v1/aigc/mcp` | name | `tags`(string) |
| 向量库 | `/api/v1/aigc/vector-stores` | name | `provider`(string) |
| 数据源 | `/api/v1/datasources` | name / description | - |

> 若某实体不存在所列列名，以实现者阅读实体后最接近的字段为准，并在任务回报中说明。

## 二、请求 DTO 校验

- 每个写接口（POST 新增 / PUT 更新）新增 `model/request/XxxSaveRequest`。
- 字段：**实体全部业务字段**（排除 `id`、`createTime`、`creator`、`updateTime`、`updater` 等审计字段），字段名与类型必须与实体一致 —— 前端现有 JSON 请求体无需改动。
- 校验：必填字段 `@NotBlank`/`@NotNull`；字符串长度 `@Size(max = 数据库列宽)`；提示语中文。
- 控制器写法：

```java
@PostMapping
@AuditLog(type = AuditLogType.AGENT, action = "新增 Agent")
public ApiResponse<Boolean> create(@Valid @RequestBody AgentSaveRequest request) {
    AigcAgent agent = new AigcAgent();
    BeanUtils.copyProperties(request, agent);
    return ApiResponse.success(aigcAgentService.save(agent));
}
```

- 更新时 `entity.setId(id)` 再 `updateById`。
- 校验失败由 `GlobalExceptionHandler` 统一映射为 HTTP 400 + 中文提示。

## 三、前端对接

- `api/aigc/_shared.ts` 扩展 `createCrudApi`：

```ts
export interface PageResult<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
}

page: (params?: Record<string, number | string | undefined>) =>
  requestClient.get<PageResult<T>>(`${basePath}/page`, { params }),
```

- 管理列表页（vxe-grid）改为服务端分页：`query(params)` 内调用 `xxxApi.page({ pageNo: params.page?.currentPage ?? 1, pageSize: params.page?.pageSize ?? 10, ...已生效筛选条件 })`，`return { items: res.records, total: res.total }`。
- 搜索/筛选条件必须传给后端（原客户端 `filter` 逻辑删除）。
- 下拉框、关联选择继续用 `list()`。

## 四、验收口径

1. `curl {basePath}/page?pageNo=1&pageSize=5` 返回 `total` 与 `records.length<=5`，`records` 按创建时间倒序。
2. `pageNo=999` 返回空 `records`，`total` 不变。
3. `pageSize=999` 按 200 截断。
4. 未登录 401；无权限账号 403。
5. 提交缺少必填字段的写请求返回 400 + 中文提示；原有合法请求行为不变。
