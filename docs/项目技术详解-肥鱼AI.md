# 肥鱼AI（LangChat 二次开发）项目全解析

> **写给谁看**：有编程基础、但没有 AI 项目经验的开发者。
> **目标**：读完这份文档，你能（1）完整讲清这个项目是什么、怎么跑、为什么这么设计；（2）把 AI 概念和代码对应起来，不再"只会背名词"；（3）能独立讲清关键设计取舍与踩坑经验。
> **阅读顺序建议**：第 1 章 → 第 2 章（AI 扫盲）→ 第 3、4 章（技术栈与架构）→ 第 5 章（核心流程，最重要）→ 其余按需。

---

## 目录

1. [项目一句话与全景](#1-项目一句话与全景)
2. [AI 零基础扫盲（12 个概念）](#2-ai-零基础扫盲12-个概念)
3. [技术栈全景](#3-技术栈全景)
4. [架构设计](#4-架构设计)
5. [核心业务流程拆解（8 条）](#5-核心业务流程拆解8-条)
6. [关键技术实现细节](#6-关键技术实现细节)
7. [数据模型](#7-数据模型)
8. [环境与部署运维](#8-环境与部署运维)
9. [我的二次开发工作（阶段化）](#9-我的二次开发工作阶段化)
10. [难点与解决方案（10 个真实问题）](#10-难点与解决方案10-个真实问题)
11. [测试与质量保障](#11-测试与质量保障)
12. [补 AI 基础的学习路线](#12-补-ai-基础的学习路线)
13. [附录：关键文件索引与常用命令](#13-附录关键文件索引与常用命令)

---

## 1. 项目一句话与全景

### 1.1 一句话

**肥鱼AI（feiyu-ai）** 是一个基于开源项目 **LangChat** 二次开发、本地化部署的**企业级 AI Agent 应用平台**：

> 用户可以在平台上**接入大模型** → **创建智能体（Agent）** → 给 Agent 挂上**知识库（RAG）**、**技能包**、**MCP 工具** → 像使用 ChatGPT 一样**流式对话**；平台同时提供**文生图、图片识别（OCR）、智能问数（Text-to-SQL）**、**应用市场、会话分享、操作审计、用量统计**等企业级功能。

一句话理解它和"直接调 ChatGPT 网页版"的区别：

| 直接调网页版 | 这个平台 |
| --- | --- |
| 模型、提示词不能改 | 模型/提示词/参数全部可在后台配置 |
| 不能用企业私有资料回答 | 知识库 RAG，用你自己的文档回答 |
| 不能调用你公司的系统 | MCP / 技能包，让模型调用工具和接口 |
| 不能审计谁用了什么 | 操作审计日志 + Token 用量统计 |
| 不能给别人用 | 多用户、角色权限（RBAC）、Agent API Key 对外开接口 |

### 1.2 功能地图

平台按菜单分为 6 大块（菜单是**数据库驱动**的，存在 `aigc_menu` 表里）：

| 模块 | 子页面 | 功能说明 | 关键技术 |
| --- | --- | --- | --- |
| 概览 | 概览 | 平台数据总览 | Vue3 + ECharts |
| 应用市场 | 应用市场、对话页 | 把 Agent 当"应用"上架，用户直接对话；流式输出、点赞点踩、会话分享、导出 Markdown、AI 自动标题 | SSE、LangChain4j |
| 智能应用 | Agent 管理、知识库、文档、智能问数 | 创建/发布 Agent；知识库与文档上传、解析、向量化、检索；Text-to-SQL 问数 | RAG、pgvector、Function Calling |
| 图片创作 | 图片生成、图片识别 | 文字生成图片；图片 OCR / 视觉理解 | 通义万相、qwen-vl、多模态 |
| 模型与能力 | 模型管理、向量存储、技能管理、MCP 管理、数据源 | 配置大模型/向量模型/向量库；管理技能包与 MCP 服务；配置业务数据库 | 多供应商适配、MCP 协议 |
| 系统管理 | 用户、角色、菜单、操作日志 | RBAC 账号体系；只读审计日志 | Sa-Token、AOP、Flyway |

其他关键能力：

- **Agent API Key**：给某个 Agent 签发 API Key，外部系统可调用它（`/v1/**` 走 API Key 鉴权，Web 端走登录 Token）。
- **OpenAI 兼容接口**：`POST /api/v1/chat/completions`，请求体兼容 OpenAI Chat Completions 格式，方便接第三方客户端。
- **公开分享页**：会话生成 `shareId` 后，`/api/v1/chat/share/{shareId}` 免登录只读访问。
- **中英文 i18n、暗色模式、响应式布局**。

### 1.3 真实运行数据（本机部署实测，可作为简历佐证）

| 指标 | 数值 |
| --- | --- |
| 后端 Java 文件 | 330 个（不含编译产物），Maven 模块 13 个 |
| 控制器 / 接口 | 30 个 Controller，152 个 HTTP 映射（GET 67 / POST 40 / PUT 23 / DELETE 22） |
| 前端 Vue/TS 文件 | 165 个（仅 `apps/langchat/src`），管理页面 20 个菜单 |
| 业务表 | 21 张 + Flyway 版本表 |
| 真实模型调用（累计） | **119 次调用，21,331 Token**（输入 19,810 / 输出 1,521） |
| 会话消息事件 | 104 条（一次回答的完整链路事件都会落库） |
| 已配置模型 | 6 个：qwen3.8-flash、qwen3.8-max、qwen3.7-text-embedding（1024 维）、wanx2.1-t2i-plus、qwen3.5-ocr、qwen3-vl-plus |
| 单元测试 | 28 个，全部通过；前端 typecheck 0 错误 |

### 1.4 术语速查表

| 术语 | 中文理解 | 本项目中的位置 |
| --- | --- | --- |
| LLM | 大语言模型，本质是"下一个 Token 预测器" | `AigcModel` 表配置的 qwen3.8 等 |
| Token | 模型计费/长度单位，约 1 汉字 ≈ 1 Token | 消息表 `input_token/output_token`、用量统计 |
| Prompt | 给模型的提示词 | `aigc_agent.system_prompt` |
| Context Window | 上下文窗口，一次能看多少 Token | 对话历史只取最近 20 条消息 |
| Embedding | 把文本变成一串数字（向量），语义相近则向量相近 | `qwen3.7-text-embedding`，1024 维 |
| Vector Store | 向量数据库，存向量并做相似度检索 | pgvector（PostgreSQL 扩展） |
| RAG | 检索增强生成：先查资料，再让模型基于资料回答 | 知识库 + `CompositeContentRetriever` |
| Chunk | 文档切片，长文档切小段才能检索 | 800 字符一片，重叠 120 |
| TopK / 阈值 | 检索返回几条、相似度低于多少不要 | 每条知识库 `max_results=5`、`min_score=0.6` |
| Function Calling | 让模型"决定调用哪个函数/工具" | 技能包、MCP、Text2SQL 的工具 |
| MCP | Model Context Protocol，模型调用外部工具的开放协议 | `McpClientManager` |
| Agent | 会使用工具、带知识库和人设的"AI 应用" | `aigc_agent` 表 + 构建器 |
| SSE | 服务器推送事件，实现"打字机"效果 | `/chat/stream`、`text/event-stream` |
| 多模态 | 文本之外的图片/文件输入 | 附件 Base64、qwen-vl |
| Text2SQL | 自然语言转 SQL 查数据库 | 智能问数 |
| 幻觉 | 模型一本正经胡说 | RAG 引用 + 提示词约束 |

---

## 2. AI 零基础扫盲（12 个概念）

> 这一章是给你的"AI 扫盲包"。每个概念都配一句"本项目里对应哪里"，看完能直接对着代码讲。

### 2.1 大模型（LLM）到底是什么

一句话：**它是一台概率机器**。你给它一段文字（Prompt），它预测"下一个 Token 最可能是什么"，一个个吐出来，连成回答。

- 它不"知道"事实，只是在海量语料上学会了"什么话接什么话"。
- 所以它**会编**（幻觉），也**不会自动知道你的内部文档**——这引出了 RAG。
- 它的能力边界由**模型本身 + 提示词 + 上下文**共同决定。

**本项目中**：模型只是"可配置的一条记录"（`aigc_model` 表：provider、model、baseUrl、apiKey、参数），平台代码通过 LangChain4j 统一调用，**换模型不用改代码**。

### 2.2 Token、上下文窗口、成本

- 模型按 Token 计费，Token 是切词后的最小单位（中文约 1 字 = 1 Token，英文约 4 字符 = 1 Token）。
- 模型有**上下文窗口上限**（例如 3 万/13 万 Token），超了会报错或截断。
- 平台必须自己控制"塞多少历史"，否则越聊越贵、越多报错。

**本项目中**：
- 每次模型调用都被监听器记录：`aigc_model_call_log`（输入/输出/总 Token、耗时、状态、场景），前端"模型监控 → 用量分析"就是读它。
- 对话历史只加载**最近 20 条消息**（`MessageWindowChatMemory.maxMessages(20)`），这就是"上下文窗口管理"的工程做法。
- 附件文档有字符上限（单文档 6 万字符、合计 12 万），防止塞爆上下文。

### 2.3 Prompt 与系统提示词（System Prompt）

- **System Prompt**：给模型的"角色设定 + 规则"，例如"你是肥鱼AI助手，回答要简洁"。
- **User Prompt**：用户输入。
- 好的 System Prompt 会显著提升稳定性（约束格式、禁止编造、要求引用资料）。

**本项目中**：`aigc_agent.system_prompt` 可在 Agent 构建器里编辑；支持 `{{变量}}` 占位符，由 `applyVariables()` 用请求里的 `variables` 替换；为空的兜底默认提示词写在 `AgentChatRuntimeServiceImpl.DEFAULT_SYSTEM_PROMPT`。

### 2.4 温度、最大长度等参数

| 参数 | 作用 | 直觉 |
| --- | --- | --- |
| temperature | 随机性 | 0 很稳、1 很发散；写代码/检索用低值 |
| top_p | 采样范围 | 与温度二选一调 |
| max_tokens | 本次最多输出多少 | 防止一次生成太长 |
| timeout | 超时 | 防止请求挂死 |

**本项目中**：这些都在"模型管理"里按模型配置（`AigcModel` 的 `temperature/topP/maxToken/timeout`），`LangChain4jModelFactory` 构建模型时注入。

### 2.5 幻觉与工程对策

模型会编造不存在的事实。工程上常见对策：

1. **RAG**：先检索真实资料，把资料塞进 Prompt，要求"只基于资料回答"——本项目知识库就是这么做的，还会把命中的文档片段作为 `rag.retrieved` 事件返回给前端展示引用。
2. **工具调用**：让模型去查数据库/调接口，而不是凭记忆答——本项目"智能问数"用只读 SQL 工具。
3. **提示词约束**：明确"不知道就说不知道"。
4. **可观测**：记录每次调用（模型、耗时、Token、错误），出问题能回溯——本项目模型监控 + 审计日志。

### 2.6 Embedding（向量化）

- 把一段文本交给**Embedding 模型**，得到固定维度的浮点数组（本项目 1024 维）。
- 语义相近的文本，向量距离相近（常用余弦相似度）。
- 这就是"让机器理解语义"存进数据库的方式。

**本项目中**：`qwen3.7-text-embedding` 生成 1024 维向量；向量库配置里必须声明维度，代码会校验"**向量模型维度 == 向量库维度**"，不一致直接报错（`EMBEDDING_DIMENSION_MISMATCH`）。

### 2.7 相似度检索：TopK 与阈值

- 用户问题也向量化，去向量库找"最相似的 N 条"（TopK）。
- 相似度低于阈值（minScore）的直接丢弃，避免拿不相关资料污染回答。

**本项目中**：每条知识库可配置 `max_results`（默认 5）和 `min_score`（默认 0.6）；多个知识库时用 `CompositeContentRetriever` 合并结果、去重、最多 12 条。

### 2.8 RAG（检索增强生成）全流程

```
上传文档 → 解析正文 → 切分 Chunk → 每个 Chunk 向量化 → 写入向量库(+元数据)
                                                         ↓
用户提问 → 问题向量化 → 向量库TopK检索 → 拼进Prompt → 大模型生成回答(带引用)
```

**本项目中**：
- 解析：`DocumentContentParseService`（Tika 解析 pdf/word/excel/ppt/md/txt/html 等）。
- 切片：`DocumentChunkingService`，默认 800 字符/重叠 120（重叠是为了不把一句话切断而丢语义）。
- 向量化：`KnowledgeDocumentIndexProcessorImpl`，异步执行，状态机 `RUNNING → COMPLETED/FAILED`，失败原因写入 `embed_error`。
- 检索：`EmbeddingStoreContentRetriever`（向量检索）+ `KeywordSegmentContentRetriever`（关键词兜底），合成 `CompositeContentRetriever`。
- 注入：LangChain4j `AiServices` 的 `contentRetriever`，自动把检索结果放进上下文。

### 2.9 Function Calling / Tool（函数调用）

- 你把"有哪些函数、参数是什么"告诉模型，模型在需要时输出"我要调用 X 函数，参数是 Y"；程序执行后把结果再交给模型，模型继续组织回答。
- 本质是：**模型负责决策，代码负责执行**。

**本项目中**：
- 技能包：标准化的 `SKILL.md` + 代码/文件，注册为 `ToolProvider`。
- MCP：外部工具服务（如浏览器、DevOps 工具），通过 MCP 协议接入。
- 智能问数：注册 `query`（只读 SQL）、`schema`（看表结构）、`time`（当前时间）三个工具。

### 2.10 MCP（Model Context Protocol）

- 一个开放协议：把"工具服务"标准化，模型/客户端用统一方式发现并调用。
- 两种传输：**HTTP（Streamable HTTP，单端点）** 与 **STDIO（本地进程，如 Docker 容器）**。

**本项目中**：`McpClientManager` 支持 `SSE/HTTP` 与 `STDIO`；对 **STDIO 做了 Docker 镜像白名单**——不在白名单里一律拒绝，因为 STDIO 会真的启动本地容器，属于高危操作。

### 2.11 Agent（智能体）

Agent = **模型 + 人设（System Prompt）+ 记忆（对话历史）+ 知识（RAG）+ 工具（技能/MCP）+ 参数** 的组合体。

**本项目中**：`aigc_agent` 一行记录就是"一个 Agent"，字段包括 `system_prompt / reasoning_model_id / knowledge_ids / skill_ids / mcp_ids / welcome_message / status` 等；`AgentRuntimeLoader` 在每次对话时把这些配置**加载成一个运行时对象** `AgentRuntimeDefinition`，再交给 `AiServices` 组装执行。

### 2.12 流式输出 SSE

- 普通 HTTP 是"等全部生成完再返回"，体验差（长回答要等十几秒）。
- SSE（Server-Sent Events）= 服务端保持连接、边生成边推送事件，前端逐字渲染，就是"打字机效果"。

**本项目中**：
- 后端：`POST /api/v1/core/agents/{agentId}/chat/stream` 返回 `Flux<ServerSentEvent<String>>`，事件类型见下表。
- 前端：`fetch` + `ReadableStream` 自己解析（不用 `EventSource`，因为要带 POST body 和 Authorization 头），按 `\n\n` 切事件块。

**事件协议**（前端常量 `CHAT_STREAM_EVENT`）：

| 事件 | 含义 |
| --- | --- |
| `message.start` | 开始 |
| `message.delta` | 增量文本（正文） |
| `rag.retrieved` | 命中的知识片段（前端展示引用） |
| `tool.before` / `tool.executed` | 工具调用前后（可展示"正在查询数据库…"） |
| `log.delta` | 过程日志（已接收/模型生成中/完成入库等） |
| `message.completed` / `message.stop` | 完成（含 Token 用量、持久化后的 messageId） |
| `analysis.table` / `analysis.echart` | 智能问数的表格/图表数据 |
| `error` | 错误 |

### 2.13 多模态（文本之外的输入）

模型可以"看图"：图片以 Base64 + MIME 类型作为消息内容的一部分发给视觉模型。

**本项目中**：聊天支持附件（最多 6 个、单个 ≤20MB），图片以 Base64 发送给 `qwen3-vl-plus`；PDF/文档由 Tika 抽正文后放进**显式引用边界**（防止文档内容被当成指令）；OCR 页面用 `qwen3.5-ocr` 识别图片文字。

### 2.14 把概念串起来（一次 RAG 对话的数据流）

```
浏览器输入问题
  → 前端 POST /api/v1/chat/completions (stream:true, 带 Authorization)
  → 后端 AgentChatRuntimeServiceImpl
      1) AgentRuntimeLoader 加载 Agent 配置(模型/知识库/技能/MCP)
      2) 取最近 20 条历史消息构建 ChatMemory
      3) 构建 ContentRetriever(向量检索 + 关键词检索) 和 ToolProvider(技能/MCP)
      4) LangChain4j AiServices 发起 TokenStream
      5) 检索命中 → SSE rag.retrieved；模型逐字生成 → SSE message.delta
      6) 完成 → 保存助手消息(input/output token) + 保存过程事件 → SSE message.completed/stop
  → 前端按事件渲染回答、引用、工具状态；Token 用量落入 aigc_model_call_log
```

---

## 3. 技术栈全景

### 3.1 后端

| 技术 | 版本 | 用途 |
| --- | --- | --- |
| Java | 17 | 语言（record/switch 表达式/文本块） |
| Spring Boot | 3.3.5 | Web/依赖注入/自动配置 |
| Spring WebFlux | Boot 内置 | **SSE 流式返回**（`Flux<ServerSentEvent>`） |
| LangChain4j | 1.20.1 | AI 编排：模型适配、AiServices、ChatMemory、RAG、Tool、MCP |
| MyBatis-Plus | 3.5.7 | ORM、Lambda 条件构造器、分页插件 |
| Sa-Token | 1.39.0 | 登录态、RBAC 权限注解、Token |
| MySQL | 8.0.33 | 业务库（21 张表） |
| PostgreSQL + pgvector | 17.8 + 0.8.6 | 向量库（1024 维） |
| Flyway | 10.10.0 | 数据库版本化迁移 |
| springdoc-openapi | 2.6.0 | OpenAPI 3 / Swagger UI |
| spring-security-crypto | Boot 内置 | BCrypt 密码哈希 |
| Hutool | 5.x | 工具类（ID/字符串等） |
| Maven | 3.8.1 | 多模块构建 |

### 3.2 前端

| 技术 | 版本 | 用途 |
| --- | --- | --- |
| Vue | 3.x | 组合式 API（`<script setup>`） |
| Vben Admin | 5.7 | 后台管理框架（monorepo、动态路由、权限、主题、i18n） |
| Naive UI | 2.x | 组件库（表格、表单、弹窗、分页） |
| Vite | 8.x | 开发服务器/构建 |
| Tailwind CSS | 4.x | 原子化样式 |
| Pinia | 3.x | 状态管理 |
| ECharts | 5.x | 图表（监控、问数） |
| markstream-vue / CodeMirror | - | Markdown 流式渲染、代码高亮 |
| Playwright | 1.x | E2E 自动化测试 |
| pnpm | 10.32.1 | 包管理与 monorepo |

### 3.3 AI 与模型

| 能力 | 模型（本机配置） | 维度/参数 |
| --- | --- | --- |
| 对话（主力/高质量） | qwen3.8-flash / qwen3.8-max | 支持流式、工具调用 |
| 向量化 | qwen3.7-text-embedding | 1024 维 |
| 文生图 | wanx2.1-t2i-plus | 异步任务轮询 |
| OCR | qwen3.5-ocr | 图片→文字 |
| 视觉理解 | qwen3-vl-plus | 图片问答/识别 |

> 平台本身**多供应商可插拔**：代码里已适配 OpenAI、DeepSeek、通义 DashScope、智谱、Gemini、Ollama，以及 OpenAI 兼容端点。

### 3.4 工程化

- Maven 多模块 + Spring Boot fat jar
- Git 分支/tag 管理阶段版本（本机有 9 个回滚 tag）
- JUnit 5 单元测试（28 个）+ MockMvc 控制器测试
- 前端 typecheck（`vue-tsc`，0 错误）+ GitHub Actions CI
- Playwright 真实浏览器 E2E（登录、分页、审计、分享页等）
- Flyway 迁移 + 备份脚本 + 生产部署文档

### 3.5 为什么这么选（面试可讲的取舍）

| 决策 | 理由 |
| --- | --- |
| 模块化单体而非微服务 | 单人/小团队可维护；AI 调用链路长、调试方便；后期可按模块拆分 |
| LangChain4j 而非自己写 HTTP 调模型 | 统一多供应商、内置 RAG/Tool/Memory/SSE 抽象，避免重复造轮子 |
| MySQL + pgvector 双库 | 业务数据强一致用 MySQL；向量检索用 PostgreSQL 扩展，避免额外引入 Milvus |
| Sa-Token 而非 Spring Security | 轻量、注解式权限直观，适合后台管理 |
| 前端 Vben Admin | 开箱即用的动态菜单/RBAC/主题/i18n，省下后台框架成本 |
| SSE 而非 WebSocket | 单向流式场景更简单，HTTP 友好，断线重连/代理兼容更好 |

---

## 4. 架构设计

### 4.1 模块化单体（13 个 Maven 模块）

```
langchat-parent
├── langchat-common（公共能力，含 6 个子模块）
│   ├── langchat-common-bom      依赖版本统一管理
│   ├── langchat-common-core     统一响应 ApiResponse、错误码、异常、分页、审计注解、全局异常处理
│   ├── langchat-common-ai       AI 相关公共类型/配置
│   ├── langchat-common-auth     登录态/当前用户工具
│   ├── langchat-common-job      定时任务公共能力
│   └── langchat-common-oss      对象存储抽象（本地/云）
├── langchat-auth        账号体系：用户/角色/菜单/权限、登录注册改密、登录风控
├── langchat-aigc        业务域：Agent/知识库/文档/模型/技能/MCP/向量库/会话/消息/审计日志
│   ├── langchat-aigc-api    Controller + 请求 DTO（对外接口层）
│   └── langchat-aigc-biz    实体/Service/Mapper（领域与持久化）
├── langchat-core        AI 运行时：对话编排、RAG、向量化、MCP、技能、图片、问数
├── langchat-datasource  业务数据源接入：连接测试、结构内省、只读 SQL 执行
├── langchat-monitor     模型调用监控：监听器、调用日志、用量统计 API
└── langchat-server      启动模块：配置、安全、审计切面、健康检查、Flyway、打包入口
```

**依赖方向**：`server → core/aigc/datasource/monitor/auth → common`。上层可以依赖下层，下层不反向依赖，避免循环依赖。

### 4.2 分层与包结构（以 aigc 为例）

```
controller（HTTP 入参校验、权限、审计注解、DTO→实体）
  ↓
service / serviceImpl（业务逻辑、事务）
  ↓
entity / mapper（MyBatis-Plus，数据库）
  ↑
model/request（请求 DTO）、model/response（响应 VO）
```

`langchat-core` 里则是"运行时"分层：`runtime/`（Agent 加载、模型工厂、RAG、MCP、技能）+ `chat/`（编排、SSE 事件组装、会话持久化）。

### 4.3 一次请求的完整链路（通用）

```
浏览器
  → Nginx/Vite 代理（/api → 8081）
  → SecurityHeadersFilter（安全响应头）
  → CORS 处理
  → Sa-Token 拦截器（/api/** 登录校验；/v1/** 用 API Key；分享接口放行）
  → Controller 方法级 @SaCheckPermission（RBAC）
  → AuditLogAspect（@AuditLog 注解，记录操作 -> 异步写 aigc_log）
  → Service（业务）
      ├─ MyBatis-Plus → MySQL
      └─ LangChain4j → 大模型 / 向量库
  → GlobalExceptionHandler（统一错误码，HTTP 状态码映射）
  → ApiResponse{code,message,data,requestId}
```

### 4.4 设计取舍

1. **双数据源**：`mysqlDataSource`（@Primary，业务库）+ `pgvectorDataSource`（仅健康检查/工具用途），Flyway 只绑定主数据源。
2. **运行时配置热加载**：模型/Agent/知识库配置都在数据库，改配置不需要重启（工厂 + 本地缓存，按 `id+updateTime` 做缓存 key）。
3. **配置与代码分离**：`application.yml` 里全部用环境变量占位符，本地/生产用 profile 区分。
4. **失败隔离**：审计写入、模型调用监控都"失败不阻断业务"（catch 后 warn）。
5. **可回滚**：每个开发阶段打 tag，数据库用 Flyway 版本化，随时可回退。

---

## 5. 核心业务流程拆解（8 条）

> 这 8 条流程是面试最容易被问的部分。"能讲清一条完整链路"比"背一堆名词"重要得多。

### 5.1 登录与鉴权

```
POST /api/v1/auth/login {username,password}
 → LoginAttemptService 检查账号是否被锁定（5 次失败/10 分钟 → 锁 10 分钟）
 → 查用户 → BCrypt 校验密码（历史明文密码校验成功后自动升级为 BCrypt）
 → 成功：StpUtil.login(userId) 生成 Token，清零失败计数，写审计日志(LOGIN)
 → 前端存 Token，后续请求头 Authorization: <token>
```

- 权限：`StpInterfaceImpl` 查用户的角色/菜单得到权限码集合，超级管理员返回 `*`。
- Controller 类上 `@SaCheckPermission("aigc:agent:list")`，无权限返回 403。
- 修改密码/重置密码后 `StpUtil.logout(userId)`，让旧 Token 立即失效（这是本项目补的一个安全漏洞）。

### 5.2 模型接入与路由

1. 管理员在"模型管理"新增模型：类型（TEXT2TEXT/EMBEDDINGS/TEXT2IMAGE/OCR/IMAGE2TEXT）、供应商、模型名、Base URL、API Key、温度、最大 Token、超时。
2. Agent 选择 `reasoning_model_id`；知识库选择 `vector_model_id`。
3. 运行时 `LangChain4jModelFactory` 根据 `provider` 走不同分支构建：OpenAI 兼容 / DashScope / 智谱 / Gemini / Ollama；构建结果按 `model id + updateTime` 缓存，改配置自动失效。
4. 每次调用都被监听器包一层，记录到 `aigc_model_call_log`（模型、场景、耗时、Token、状态、错误）。

**要点**：模型 API Key 在列表/详情接口中**脱敏**（`sk-***abcd`）；更新时若传回脱敏值则保留库中原 Key，避免前端回填覆盖。

### 5.3 文档上传 → 解析 → 切片 → 向量化

```
上传(POST /api/v1/aigc/docs/upload, multipart)
 → 扩展名白名单校验(pdf/doc/docx/xls/xlsx/ppt/pptx/md/txt/csv/html/json)、大小≤100MB
 → 文件落 OSS(本地磁盘) -> aigc_oss 记录
 → aigc_docs 记录(状态 PENDING/WAITING)
 → 触发索引：POST /api/v1/core/knowledges/{id}/index (或页面上点"向量化")
 → KnowledgeIndexAsyncService 异步线程池执行
 → KnowledgeDocumentIndexProcessorImpl.processDocument:
     1) 校验知识库、向量库、向量模型维度一致
     2) 清理旧向量(按 old segment ids + 按 docsId 元数据兜底)
     3) 删除旧分段 → 解析正文(Tika) → 切分(800/120) → 保存 aigc_segment
     4) embedAll 批量向量化 → embeddingStore.addAll(ids, embeddings, segments)
     5) 状态更新 COMPLETED/FAILED(错误信息入库)
```

- **状态机**：`RUNNING → COMPLETED / FAILED`；`KnowledgeIndexTaskWatchdog` 定时兜底，把超时未结束的任务标记失败（防止"永远运行中"）。
- **幂等**：重复向量化不会让向量翻倍（这是本项目修复的一个真实 Bug，见第 10 章）。

### 5.4 RAG 问答

1. Agent 绑定知识库（`knowledge_ids`）。
2. 用户提问 → `AgentChatRuntimeServiceImpl.buildContentRetriever`：
   - 每个知识库一个**向量检索器**（`EmbeddingStoreContentRetriever`，TopK=知识库配置，minScore=0.6）；
   - 同时加一个**关键词检索器**（`KeywordSegmentContentRetriever`，对 `aigc_segment.content` 做 LIKE + 词频/位置打分），作为向量检索的兜底（例如专有名词、编号，向量检索不一定准）。
   - `CompositeContentRetriever` 合并去重，最多 12 条。
3. LangChain4j 把命中片段注入 Prompt，模型基于资料回答。
4. 检索结果通过 SSE `rag.retrieved` 推给前端，前端展示"引用了哪些文档/片段"。

### 5.5 普通对话（SSE 全链路）

```
前端 POST /api/v1/chat/completions {agentId, conversationId?, message, attachments?, variables?, stream:true}
 → OpenAiAgentChatController → AgentChatRuntimeServiceImpl.streamChat
 → 校验请求(消息非空、附件数/大小限制)
 → AgentRuntimeLoader.load(agentId)  # 模型+知识库+技能+MCP
 → ensureConversation：无会话则新建（标题为首条消息截断或"新会话 HH:mm"）
 → 构建 AiService：streamingChatModel + chatMemory(最近20条) + contentRetriever + tools + mcp
 → 保存用户消息 aigc_message
 → TokenStream.start()
     onPartialResponse → 累加文本 → SSE message.delta
     onRetrieved      → SSE rag.retrieved
     beforeToolExecution / onToolExecuted → SSE tool.*
     onCompleteResponse → 保存助手消息(内容/Token/耗时/finish_reason) → SSE message.completed/stop
     onError → SSE error + 失败事件落库
 → 所有过程事件批量写入 aigc_message_event（可回溯整条链路）
```

补充：
- **自动标题**：首轮回答完成后可调用 `title/generate`，让模型把首轮问答概括为 ≤16 字标题。
- **点赞点踩**：`POST /chat/messages/{id}/feedback`，`aigc_message.likes` = true/false/null。
- **分享**：`POST /chat/conversations/{id}/share` 生成 `shareId`，公开接口免登录只读。
- **导出**：`GET /chat/conversations/{id}/export` 返回 `text/markdown` 附件。

### 5.6 Agent + 技能 + MCP 工具调用

- **技能包**：上传 zip/文件夹（根目录必须含 `SKILL.md`），平台解压到工作区并注册为工具（`AgentToolRegistry`），模型按需调用。
- **MCP**：配置 MCP 服务（HTTP 端点或 Docker 镜像），`McpClientManager` 建立会话并缓存（TTL 1 小时，配置变更事件触发立即失效），暴露工具列表给模型；STDIO 模式必须命中 Docker 镜像白名单。
- **安全边界**：工具执行有超时（默认 30s）；MCP 会话按服务配置隔离；STDIO 命令由平台拼装，用户不能直接注入任意命令。

### 5.7 文生图 / OCR / 多模态附件

- **文生图**：`OpenAiImageService` 按 provider 分三种实现（OpenAI 兼容同步、通义万相异步轮询、Gemini），返回图片 URL/Base64，前端展示下载。
- **OCR/视觉**：`recognizeText(modelId, prompt, imageDataUrl)`，图片转 Base64 DataURL 调视觉模型。
- **多模态附件**：图片以真实 Base64 + MIME 发送；PDF/文档由 Tika 抽正文，放入 `【文档引用】...【/文档引用】` 边界，防止"文档里的文字"被当成用户指令（提示词注入防护）。

### 5.8 智能问数（Text-to-SQL）

```
用户在"智能问数"选择数据源 + 勾选允许 AI 检索的表 → 提问
 → DataAnalysisServiceImpl 构建 3 个工具：
     query(sql, title, chartType)  # 只读 SQL，执行并返回列/行
     schema()                      # 返回授权表结构
     time()                        # 当前时间（处理"今天/本月"）
 → 系统提示词包含表结构、字段注释、只读约束
 → 模型生成 SQL → beforeToolExecution 展示"正在查询" → 执行
     datasourceService.executeReadOnlyQuery(datasourceId, sql, allowedTables)
       - 只允许 SELECT/WITH
       - 只允许访问已授权的表
       - 加超时与行数限制
 → 结果通过 SSE analysis.table / analysis.echart 推给前端渲染表格/图表
```

**安全要点**：这是"AI 直接产出 SQL"的高风险场景，所以做了**只读校验 + 表白名单**，绝不能让它执行 DROP/DELETE。

---

## 6. 关键技术实现细节

### 6.1 安全基线

| 项 | 实现 |
| --- | --- |
| 密码 | BCrypt 哈希入库；历史明文登录后自动升级；管理员重置密码 |
| 登录风控 | 5 次失败/10 分钟 → 锁定 10 分钟，超限返回 429 |
| 登录强制 | Sa-Token 拦截 `/api/**`，白名单仅登录/注册/分享/文档 |
| RBAC | `@SaCheckPermission` + 菜单/角色/权限表；超管 `*` 通配 |
| 上传限制 | 文档扩展名白名单 + ≤100MB；聊天附件仅图片/PDF + ≤20MB；技能包仅 zip |
| MCP 收口 | STDIO 仅允许白名单 Docker 镜像 |
| CORS | 来源可配置（`LANGCHAT_CORS_ALLOWED_ORIGINS`），只允许必要头/方法 |
| 安全头 | `X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`、`Permissions-Policy` |
| 数据脱敏 | 模型 apiKey / 数据源密码 / 向量库密码 列表与详情脱敏；审计参数自动掩码 |
| 错误收敛 | 500 不返回堆栈，只返回通用文案 + requestId；未登录 401 / 无权限 403 / 参数错 400 / 方法错 405 / 不存在 404 |

### 6.2 分页与 DTO 校验（二次开发重点）

- **分页**：统一 `GET {资源}/page?pageNo=&pageSize=&keyword=&...`，返回 `PageResult{records,total,pageNo,pageSize}`；`PageQuery` 归一化（pageNo≥1，pageSize 默认 20、上限 200）；MyBatis-Plus `PaginationInnerInterceptor` 必须有（否则分页不生效，会退化成全表查询）。
- **兼容**：原全量列表接口保留，下拉框/关联选择不受影响。
- **DTO 校验**：10 个 `XxxSaveRequest`，必填 `@NotBlank`、长度 `@Size`，`@Valid` 触发，`GlobalExceptionHandler` 统一转 400 中文提示。字段与实体一一对应，前端现有 JSON 不需要改。

### 6.3 异常处理与错误码

- `ApiResponse<T>{code, message, data, requestId}` 统一响应。
- `BizException(code, message)` 业务异常 → `GlobalExceptionHandler` 按错误码前缀映射 HTTP 状态：
  - `AUTH_` → 401；`FORBIDDEN_` → 403；`AUTH_LOGIN_RATE_LIMIT` → 429；其余 → 400。
- 参数校验失败 → 400 + 具体字段提示；非法 JSON / 参数类型错误 → 400；未知异常 → 500 通用文案。

### 6.4 审计日志（AOP + 异步）

```
@AuditLog(type=USER, action="更新用户") 标注在 Controller 方法
 → AuditLogAspect 环绕：
    请求线程：构造 AigcLog（用户、IP、UA、method、URL、耗时、参数JSON脱敏后截断）
    → AuditLogWriter @Async("auditLogExecutor") 异步写 aigc_log（失败只 warn）
    业务异常：记录后原样抛出，审计带 error 字段
 → 查询：GET /api/v1/aigc/logs 只读分页（type/username/keyword/时间范围）
 → 清理：AuditLogRetentionJob 每天 03:30 删除 90 天前记录
```

- 线程池：core=1 / max=4 / queue=1000 / CallerRunsPolicy。
- `aigc_log.params` 从 varchar(255) 扩为 TEXT（Flyway V2），切面截断上限提到 4000，保证长参数完整可查。
- 关键点：审计**不能阻断业务**；异步化后接口响应不再等待落库。

### 6.5 Flyway 数据库版本化（接管存量库）

**问题**：项目已经跑起来、库里有数据，直接引入 Flyway 会报"non-empty schema but no schema history table"。

**解法**：
```yaml
spring:
  flyway:
    enabled: ${LANGCHAT_FLYWAY_ENABLED:true}
    baseline-on-migrate: true   # 已有库：把 V1 标记为已应用（不执行）
    baseline-version: 1
    locations: classpath:db/migration
```
- `V1__baseline_schema.sql`：全库 `CREATE TABLE IF NOT EXISTS` + 种子数据 `INSERT IGNORE`（给**全新空库**用）。
- `V2__audit_log_enhance.sql`：`params` 扩 TEXT + 3 个索引（给**存量库**用）。
- 迁移脚本先在临时库 `langchat_flyway_check` 上验证，确认无误再对正式库执行。
- 生产回滚：`git checkout` 旧版本 + 数据库用备份恢复；迁移脚本只增不改。

### 6.6 模型调用监控

- 通过 LangChain4j 的 `ChatModelListener` / `EmbeddingModelListener` 无侵入记录每次调用。
- 表 `aigc_model_call_log`：模型、供应商、场景（AGENT_CHAT/KNOWLEDGE_INDEX/KNOWLEDGE_RETRIEVE/DATA_ANALYSIS/…）、输入/输出/总 Token、耗时、状态、错误。
- 接口：
  - `GET /api/v1/monitor/model-calls/overview`：指标卡 + 趋势 + 分布；
  - `GET /api/v1/monitor/model-calls/usage?windowDays=7`：配额（每日 Token 上限）、总量、按日趋势、按模型/场景/类型分布、最近调用明细分页。
- 意义：AI 应用"不可观测就没有运维"，这是从 Demo 走向产品的分水岭。

### 6.7 前端关键实现

| 能力 | 实现 |
| --- | --- |
| 动态菜单/路由 | 登录后调 `/auth/access-routes`，后端按角色返回菜单，前端递归生成路由（Vben 机制） |
| 主题 | 设计令牌（CSS 变量）+ Naive UI 覆盖；主色 `hsl(212 100% 45%)`；圆角 0.5rem；暗色模式 |
| 品牌 | `products.ts`/`preferences.ts`/`index.html`/登录页/关于页统一"肥鱼AI"；移除开源外链 |
| SSE 客户端 | `fetch` + `ReadableStream` + `TextDecoder`，按 `\n\n` 切块解析 `event:`/`data:`；支持中断（AbortSignal） |
| 聊天组件 | `components/LcChat`：消息气泡、Markdown 流式渲染、复制/重新生成/点赞、附件上传、语音输入 |
| 表格/分页 | vxe-grid（服务端分页）或卡片 + NPagination；搜索防抖、空页回退 |
| 表单 | Vben 表单适配器 + zod 校验；图片/文件上传 |
| i18n | zh-CN / en-US；菜单名来自数据库（中文） |

---

## 7. 数据模型

### 7.1 表分组

**账号与权限（5 张）**
- `aigc_user`（用户：username/real_name/password/status/phone/email/avatar）
- `aigc_role`（角色）、`aigc_menu`（菜单/按钮权限，含 perms 权限码）
- `aigc_user_role`、`aigc_role_menu`（关联表）

**AI 配置（5 张）**
- `aigc_model`（模型：type/provider/model/base_url/api_key/参数/config json）
- `aigc_vector_store`（向量库：provider/host/port/db/table/dimension/账号密码）
- `aigc_skill`（技能包：名称/版本/文件数/本地路径/OSS）
- `aigc_mcp`（MCP 服务：transport/sse_url/docker_image/headers/timeout）
- `aigc_datasource`（业务数据源：db_type/连接信息/structure_json）

**Agent 与知识库（4 张）**
- `aigc_agent`（Agent：system_prompt/reasoning_model_id/knowledge_ids/skill_ids/mcp_ids/状态）
- `aigc_knowledge`（知识库：vector_store_id/vector_model_id/max_results/min_score）
- `aigc_docs`（文档：knowledge_id/oss_id/解析与向量化状态/时间戳）
- `aigc_segment`（切片：docs_id/knowledge_id/content/position/enabled/index_hash）

**对话（3 张）**
- `aigc_conversation`（会话：agent_id/title/share_id/creator）
- `aigc_message`（消息：role/content/input_token/output_token/likes/attachments/trace_info）
- `aigc_message_event`（消息事件：一次回答的阶段性事件，用于链路回溯）

**运维（4 张）**
- `aigc_model_call_log`（模型调用日志）
- `aigc_log`（操作审计）
- `aigc_oss`（文件元数据）
- `aigc_agent_api_key`（Agent 对外 API Key）

**版本（1 张）**：`flyway_schema_history`

### 7.2 核心关系（文字版 ER）

```
aigc_user 1─n aigc_conversation 1─n aigc_message 1─n aigc_message_event
aigc_agent n─1 aigc_model(对话模型)
aigc_agent n─n aigc_knowledge / aigc_skill / aigc_mcp   (通过 JSON 字段存 id 列表)
aigc_knowledge n─1 aigc_vector_store, n─1 aigc_model(向量模型)
aigc_knowledge 1─n aigc_docs 1─n aigc_segment
aigc_docs n─1 aigc_oss
aigc_user n─n aigc_role n─n aigc_menu
```

### 7.3 设计要点

1. **JSON 字段**：Agent 的 `knowledge_ids/skill_ids/mcp_ids`、模型 `config_json`、数据源 `structure_json` 用 JSON 字符串存储——灵活但要注意校验和解析兜底（代码里有 `parseIdList` 兼容 JSON 数组与逗号分隔两种历史格式）。
2. **时间戳统一用 bigint（毫秒）**，避免时区问题；`create_time/update_time/creator/updater` 所有表都有（审计字段）。
3. **状态字段用字符串枚举**（如 `embedStatus`），便于前端展示和扩展。
4. **消息事件单独成表**：一次回答的 `request.accepted → model.generating → tool.before → tool.executed → message.completed` 都落库，出问题能复盘（这也是 SSE 事件可追溯的基础）。

---

## 8. 环境与部署运维

### 8.1 本机拓扑

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| 前端（Vite dev） | http://localhost:5888 | 代理 `/api` → 8081 |
| 后端（Spring Boot） | http://localhost:8081 | fat jar 后台运行 |
| MySQL | 127.0.0.1:3308 | root/123456，库 `langchat` |
| PostgreSQL + pgvector | 127.0.0.1:5433 | 库 `langchat_vector`，扩展 `vector` |
| 文件存储 | `F:/langchat/data` | 本地 OSS 实现 |

### 8.2 启动顺序

```powershell
# 1. MySQL 已作为服务运行（3308）
# 2. pgvector（便携版）
F:\pgsql-portable\start-pg.ps1

# 3. 后端（构建 + 启动，日志 logs\backend.log）
$env:JAVA_HOME='E:\java\jdk17'
E:\maven\apache-maven-3.8.1\bin\mvn.cmd -B -pl langchat-server -am -DskipTests package
Start-Process cmd.exe -ArgumentList '/c','java -jar langchat-server\target\langchat-server.jar --spring.profiles.active=local > logs\backend.log 2>&1' -WorkingDirectory F:\langchat -WindowStyle Hidden

# 4. 前端
cd F:\langchat\langchat-ui
pnpm --filter @vben/web-naive dev    # 5888
```

> 注意：后端 `--spring.profiles.active=local` 读取 `application-local.yml`（gitignored，含数据库连接、管理员账号、存储路径）。不要用系统默认 `mvn`（版本太旧）。

### 8.3 环境变量清单（生产必配）

| 变量 | 说明 |
| --- | --- |
| `LANGCHAT_MYSQL_URL/USERNAME/PASSWORD` | 主库 |
| `LANGCHAT_PGVECTOR_URL/USERNAME/PASSWORD` | 向量库 |
| `LANGCHAT_CORS_ALLOWED_ORIGINS` | **必填**，生产前端域名 |
| `LANGCHAT_FLYWAY_ENABLED` | 默认 true |
| `LANGCHAT_AUDIT_RETENTION_DAYS / _CRON` | 审计保留天数/清理时间 |
| `LANGCHAT_MONITOR_DAILY_TOKEN_LIMIT` | 每日 Token 配额（0=不限） |
| `LANGCHAT_REGISTER_ENABLED` / `LANGCHAT_DEFAULT_ROLE_CODE` | 是否开放注册/默认角色 |
| `LANGCHAT_MCP_STDIO_IMAGE_WHITELIST` | STDIO Docker 镜像白名单（默认空=全禁） |
| `SPRINGDOC_API_DOCS_ENABLED` / `SPRINGDOC_SWAGGER_UI_ENABLED` | 生产建议 false |
| `LANGCHAT_SKILL_WORKSPACE_DIR` | 技能工作区目录 |

### 8.4 生产部署清单（`docs/deploy/production.md`）

1. 关闭 Swagger；CORS 只允许正式域名；`LANGCHAT_REGISTER_ENABLED` 按需关闭。
2. 数据库先备份再启动（Flyway 会自动迁移）。
3. 密钥（模型 API Key、数据库密码）通过环境变量/密钥管理注入，不写配置文件、不进 Git。
4. JVM 参数、日志滚动、健康检查 `/actuator/health`。
5. 升级流程：备份 → 拉新包 → 停旧进程 → 启动新进程 → 校验 Flyway 日志与健康检查 → 冒烟测试。
6. 回滚流程：停进程 → 恢复旧包 → 恢复数据库备份（Flyway 迁移不可逆，务必先备份）。

### 8.5 备份与迁移演练（本项目实践）

- `mysqldump --single-transaction` 备份到 `logs/db-backup-before-stage3d.sql`。
- Flyway 迁移前用独立临时库 `langchat_flyway_check` 导入生产备份并执行迁移脚本，验证通过后才操作正式库。
- pgvector 便携版有 `start-pg.ps1 / stop-pg.ps1`，数据目录与安装目录分离，方便迁移。

---

## 9. 我的二次开发工作（阶段化）

> 这是简历的核心素材。开源项目本身提供基础能力，以下是我做的**本地部署 + 安全加固 + 品牌化 + 体验增强 + 工程化改造**，按阶段打 tag，可回滚、可验证。

| 阶段 | Tag | 主要工作 | 验收 |
| --- | --- | --- | --- |
| 基线部署 | `baseline-deploy-20261109` | 拉取开源项目、解决端口冲突、本地化配置、MySQL 初始化、pgvector 便携部署、前后端跑通、真实大模型接入 | 健康检查 UP、登录成功、SSE 对话成功、RAG 命中 |
| 安全基线 | `stage-1-security` | Sa-Token 登录强制 + RBAC；BCrypt 密码与明文平滑迁移；敏感字段脱敏；登录风控（5/10/10）；上传白名单；MCP STDIO 白名单；CORS + 安全头；错误信息收敛 | 401/403/429 边界验证、越权测试、上传拒绝测试 |
| 品牌化 | `stage-2-brand` | 品牌"肥鱼AI"、Logo、登录页/概览/关于页重做、设计令牌（风格 A 极简商务）、移除开源外链、保留 License 合规 | Playwright 截图、0 console error |
| 全站美化 | `stage-2b-*` | 20 个页面统一列表/表格/表单规范、Agent 构建器、知识库文档、应用市场对话页、工具与监控页 | 逐页截图 + 功能回归 |
| 账号体系 | `stage-3a-account` | 注册/改密/资料编辑/管理员重置密码；删除用户级联；改密后 Token 吊销；删除文档清理向量 | 接口 + UI 验证 |
| 对话体验 | `stage-3b-chat` | 点赞点踩、会话分享+公开分享页、Markdown 导出、AI 自动标题、补齐会话 CRUD | E2E 全流程 |
| 工程化 | `stage-3c-engineering` | 操作审计日志（AOP + 只读页面）、用量统计 API/页面、OpenAPI 3、分页工具、单元测试+CI、修复基线 typecheck 错误 | 43 项 API 验收、17 页巡检、typecheck 0 |
| 分页与基础设施 | `stage-3d-pagination` | 11 个资源服务端分页 + 10 个请求 DTO 校验、Flyway 迁移、审计异步化+清理、28 个单测、生产部署文档、全局异常收敛 | 38 项 API 验收 + 11 页 E2E + 28 单测 + typecheck 0 |

**总代码量**：后端 330 个 Java 文件 / 13 模块 / 152 个接口；前端 165 个 Vue/TS 文件 / 20 个菜单页面。

---

## 10. 难点与解决方案（10 个真实问题）

> 面试时讲"我遇到什么问题、怎么定位、怎么解决、怎么验证"，比罗列技术栈有说服力。以下都是本项目真实踩过的坑。

**1. 分页不生效（只返回全量数据）**
- 现象：前端传 `pageNo/pageSize` 但返回全部数据。
- 定位：MyBatis-Plus 的 `PaginationInnerInterceptor` 没有注册，`IPage` 不生效。
- 解决：新增 `MybatisPlusConfiguration` 注册分页插件，设置 `maxLimit=500`、`overflow=false`。
- 验证：`pageNo=999` 返回空记录、`pageSize=999` 被截断为 200。

**2. 重新向量化后向量翻倍、检索到旧内容**
- 现象：同一文档重新向量化，MySQL 分段 2 条，pgvector 里 4 条，RAG 还能召回改名前的旧文本。
- 根因：重建分段时生成全新 UUID，只删了 MySQL 分段，旧向量永远残留。
- 解决：重建前按旧 segment id 删向量，再按 `docsId` 元数据兜底删；写入失败时 best-effort 清理半成品。
- 验证：MySQL 行数 = 向量库行数；重新向量化 2 次不翻倍；RAG 命中新内容。

**3. 引入 Flyway 时存量库报错 / 担心改坏数据**
- 解决：`baseline-on-migrate` + `baseline-version=1`，V1 只服务全新库，V2 才真改结构；迁移脚本先在临时库演练；迁移前 mysqldump 备份。
- 验证：启动日志 `Successfully baselined schema with version: 1` → `Successfully applied 1 migration`；正式库 `params` 变 TEXT、3 个索引存在。

**4. 端口冲突导致后端起不来**
- 现象：8080 被 Steam 客户端占用。
- 解决：后端改 8081，前端 Vite 代理同步改；记录到部署文档。
- 经验：Windows 上先 `netstat -ano | findstr 端口` 定位占用进程。

**5. 审计日志用户名显示内部 ID（`user_admin`）**
- 解决：切面里用 `AuthService` 解析真实用户名（admin）；历史数据 SQL 归一化；异步化后仍保持。
- 经验：审计信息要"人可读"，否则等于没记录。

**6. 审计参数被截断到 255 字符**
- 解决：Flyway 扩列 TEXT，切面截断上限 4000，保留敏感字段脱敏；验证 625 字符的完整参数落库。

**7. 前后端 Token/权限不一致（401/403 混乱）**
- 现象：日志接口 POST 返回 500、未知路径 500、错误 HTTP 方法 500。
- 解决：`GlobalExceptionHandler` 补 404/405 映射；`/v1/**`（API Key）与 `/api/**`（登录）分开鉴权；分享接口放行。
- 经验：错误码映射要成体系，前端才能做统一提示。

**8. 修改密码后旧 Token 仍有效**
- 解决：改密/重置密码后 `StpUtil.logout(userId)` 强制下线；补充越权与登出回归测试。

**9. 前端 typecheck 基线错误导致 CI 不可用**
- 解决：修复 2 个历史类型错误 → 0 错误；CI 增加 typecheck 作为门禁。
- 经验：接手项目先建立"质量基线"，否则任何改动都无法判断是否引入回归。

**10. 本机环境细节坑（Windows/PowerShell/编码）**
- Maven 3.6.1 太旧，需 3.8.1；PowerShell 5.1 脚本含中文必须 UTF-8 BOM；`mysql -h127.0.0.1` 被 PowerShell 曲解需用 `--host=`；jar 运行中无法被 repackage 覆盖需先停进程。
- 经验：把环境坑写进 README/部署文档，避免重复踩。

---

## 11. 测试与质量保障

| 层次 | 做了什么 | 结果 |
| --- | --- | --- |
| 单元测试 | 登录风控、脱敏工具、分页工具、审计异步写入、审计清理任务；MockMvc 测试分页/校验 | **28/28 通过** |
| 接口验收 | PowerShell + curl 脚本覆盖分页、过滤、越界、DTO 400、审计异步、权限 401 | 38/38 通过 |
| E2E | Playwright 真实浏览器：登录（滑块）、11 个管理页 `/page` 网络断言、搜索、真实翻页、暗色模式 | 15 项全 PASS |
| 页面巡检 | 9 个非分页页面回归，检查 console/page error、横向滚动 | 全 PASS，error=0 |
| 静态检查 | 前端 `vue-tsc` typecheck | **0 错误** |
| CI | GitHub Actions：后端 `mvn test` + 前端 typecheck/build（本地等价验证通过，未在 GitHub 实跑） | 配置就绪 |

**测试策略经验**：
- AI 应用测试要分两层：**确定性逻辑**（权限/分页/校验/审计）用单测和接口测试兜住；**非确定性逻辑**（模型输出）用"冒烟 + 断言结构"（HTTP 200、SSE 事件齐全、Token 记录存在），不要断言具体文案。
- E2E 一定要用真实账号登录 + 真实网络请求，否则前端"看起来对"但接口参数错了也发现不了。

---

## 12. 补 AI 基础的学习路线

> 你有编程基础，缺的是 AI 领域的"常识 + 手感"。按下面顺序 1-2 周可以补到能面试的程度。

| 顺序 | 学什么 | 怎么学（最少必要） |
| --- | --- | --- |
| 1 | LLM 基本概念 | 读完本文第 2 章 + 用平台/API 亲手调 10 次不同模型 |
| 2 | Prompt 工程 | 改 Agent 的 system_prompt，对比输出差异（要求 JSON/引用/拒绝编造） |
| 3 | Embedding 与向量检索 | 给知识库传 3 篇文档，观察 chunk 数量、检索命中、minScore 调大调小 |
| 4 | RAG | 画一遍 5.3/5.4 的流程图，讲清"为什么要切片""为什么要重叠" |
| 5 | Function Calling | 读 `DataAnalysisServiceImpl` 的 3 个工具定义，理解 schema→模型决策→执行→回填 |
| 6 | MCP | 读 `McpClientManager`，理解 HTTP 与 STDIO 两种传输、会话缓存、白名单 |
| 7 | 多模态 | 用聊天的图片附件问"图里有什么"，理解 Base64 图片消息 |
| 8 | 流式与并发 | 读 SSE 前后端代码，理解背压/中断/超时 |
| 9 | 评测与可观测 | 看"模型监控"页，理解 Token/耗时/成功率对成本与稳定性的意义 |
| 10 | 安全与合规 | 复习第 6.1 节，理解 AI 应用的注入、越权、数据泄露风险 |

**动手练习建议**：
1. 新增一个"摘要"Agent（System Prompt 要求输出 3 条要点），用普通对话验证。
2. 新建知识库 + 上传 PDF + 向量化 + 在 Agent 里绑定 + 提问验证引用。
3. 给数据源配一张表，用"智能问数"问"最近 7 天各状态数量"，观察生成的 SQL（只读）。
4. 在操作日志页找到你刚才操作的审计记录，解释参数脱敏。

---

## 13. 附录：关键文件索引与常用命令

### 14.1 后端关键文件

| 文件 | 作用 |
| --- | --- |
| `langchat-server/.../LangchatServerApplication.java` | 启动类（扫描、MapperScan、@EnableAsync） |
| `langchat-server/.../config/SaTokenConfigure.java` | 登录拦截、放行规则 |
| `langchat-server/.../config/MybatisPlusConfiguration.java` | 分页插件 |
| `langchat-server/.../config/DataSourceConfiguration.java` | 双数据源 |
| `langchat-server/.../config/OpenApiConfiguration.java` | OpenAPI 3 |
| `langchat-server/.../audit/AuditLogAspect.java` | 审计切面 |
| `langchat-server/.../job/AuditLogRetentionJob.java` | 审计清理 |
| `langchat-common/.../GlobalExceptionHandler.java` | 全局异常映射 |
| `langchat-common/.../page/PageQuery.java`、`PageResult.java` | 分页 |
| `langchat-auth/.../AuthServiceImpl.java` | 登录/注册/改密 |
| `langchat-auth/.../LoginAttemptService.java` | 登录风控 |
| `langchat-core/.../AgentChatRuntimeServiceImpl.java` | **对话编排核心**（SSE/记忆/RAG/工具/持久化） |
| `langchat-core/.../AgentRuntimeLoader.java` | 加载 Agent 运行时配置 |
| `langchat-core/.../factory/LangChain4jModelFactory.java` | **多供应商模型工厂** |
| `langchat-core/.../factory/VectorStoreFactory.java` | 向量库工厂（pgvector/milvus） |
| `langchat-core/.../rag/DocumentChunkingService.java` | 文档切片 |
| `langchat-core/.../rag/KnowledgeSearchService.java` | 关键词检索兜底 |
| `langchat-core/.../KnowledgeDocumentIndexProcessorImpl.java` | **向量化流水线** |
| `langchat-core/.../mcp/McpClientManager.java` | MCP 客户端/会话 |
| `langchat-core/.../DataAnalysisServiceImpl.java` | Text2SQL |
| `langchat-monitor/.../ModelCallStatisticsService.java` | 用量统计 |
| `langchat-server/src/main/resources/db/migration/` | Flyway V1/V2 |
| `docs/api/stage3d-pagination.md` | 分页/DTO 接口契约 |
| `docs/deploy/production.md` | 生产部署清单 |

### 14.2 前端关键文件

| 文件 | 作用 |
| --- | --- |
| `apps/langchat/src/preferences.ts` | 品牌名、主题、布局偏好 |
| `apps/langchat/src/api/request.ts` | Axios 封装、Token 注入、401/403 处理 |
| `apps/langchat/src/api/aigc/chat.ts` | **SSE 流式请求 + 会话/消息/分享/导出 API** |
| `apps/langchat/src/views/shared/chat/use-chat-runtime.ts` | 对话运行时（发消息、流事件处理） |
| `apps/langchat/src/components/LcChat` | 聊天 UI 组件 |
| `apps/langchat/src/views/agents/*` | Agent 列表/构建器/调试面板 |
| `apps/langchat/src/views/knowledges/*`、`docs/*` | 知识库与文档 |
| `apps/langchat/src/views/models/*`、`skills/*`、`mcp/*`、`vector-stores/*`、`datasource/*` | 配置管理 |
| `apps/langchat/src/views/logs/index.vue` | 操作日志页 |
| `apps/langchat/src/views/monitor/model-call/index.vue` | 模型监控/用量分析 |
| `apps/langchat/src/router/` | 静态路由 + 动态菜单路由生成 |

### 14.3 常用命令

```powershell
# 后端构建（跳过测试）
$env:JAVA_HOME='E:\java\jdk17'
E:\maven\apache-maven-3.8.1\bin\mvn.cmd -B -pl langchat-server -am -DskipTests package

# 后端测试
E:\maven\apache-maven-3.8.1\bin\mvn.cmd -B -pl langchat-server -am test

# 前端
cd F:\langchat\langchat-ui
pnpm --filter @vben/web-naive typecheck
pnpm --filter @vben/web-naive dev

# 健康检查
curl http://127.0.0.1:8081/actuator/health

# 数据库备份
E:\MySQL\MySQL8\mysql-8.0.33-winx64\bin\mysqldump.exe --host=127.0.0.1 --port=3308 --user=root --password=123456 --databases langchat --single-transaction --result-file=backup.sql
```

### 14.4 一句话总结

> 这个项目让你同时具备三种能力：**AI 应用工程能力**（RAG/Agent/MCP/流式/多模态）、**后端工程能力**（安全/RBAC/分页/审计/迁移/测试）、**前端交付能力**（动态路由/主题/SSE/可视化）。面试时围绕"一条 RAG 对话链路 + 一个安全加固案例 + 一个工程化改造"讲透，就足够撑起一个 AI 应用开发岗位的项目经历。
