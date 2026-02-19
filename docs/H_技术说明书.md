# H. 技术说明书

## 系统背景
NSBH（Nanobot Spring Boot Host）是一个轻量代理宿主：
- 提供会话式聊天 API
- 支持工具调用与权限控制
- 持久化对话记忆
- 提供摘要压缩与每日摘要任务

目标是可调试、可扩展、安全可控。

## 架构设计
采用单体分层架构：
- API 层：`api`（WebFlux）
- 业务编排层：`agent`
- 工具层：`tools`
- 持久化层：`memory.entity/repo`
- 调度层：`scheduler`
- 基础设施层：`config`、`logging`、`resources`

主流程：
`Controller -> ConversationService -> LlmClient/ToolService -> Repository -> DB`

## 模块说明
- API：对外 REST 契约与错误响应。
- Agent：聊天主流程、上下文窗口、摘要策略、LLM 适配。
- Tools：工具注册与执行策略（allowlist/permissions/timeout/size）。
- Memory：R2DBC Conversation/Message 实体与 Reactive Repository。
- Scheduler：每日摘要写入 `DAILY_SUMMARY`。
- Config：集中配置绑定（`nsbh.*`）。
- Logging：全局 JSON 日志与工具审计日志。

## 核心流程
1. 创建会话：生成 `conversationId`。
2. 聊天：
   - 写 USER
   - 触发压缩（可选）
   - 构建 Prompt
   - 调 LLM 首轮
   - 如需工具：执行工具并写 TOOL，再调 LLM 最终回复
   - 写 ASSISTANT
3. 查询消息：按时间升序返回。
4. 调度摘要：按 cron 扫描近 24h 会话并写 `DAILY_SUMMARY`。

## 数据模型
- `ConversationEntity`
  - `id`, `createdAt`, `updatedAt`
- `MessageEntity`
  - `id`, `conversationId`, `role`, `type`, `content`, `toolName`, `toolCallId`, `createdAt`
- 枚举：
  - `MessageRole`: `SYSTEM|USER|ASSISTANT|TOOL`
  - `MessageType`: `NORMAL|SUMMARY|DAILY_SUMMARY`

数据库：
- 默认 H2（内存）
- 可切换 Postgres profile
- schema 文件：`db/migration/V1__init_schema.sql`
- 运行时由 R2DBC 初始化器加载；同时保留 Flyway 配置

## 可扩展性分析
- LLM 可扩展：实现 `LlmClient` 并通过配置切换。
- 工具可扩展：新增 `Tool` + `@NsbhTool` 自动被注册。
- 安全可扩展：工具权限模型可新增权限枚举与校验规则。
- 存储可扩展：已支持 H2/Postgres；可继续引入读写分离或缓存。
- 调度可扩展：按同样模式新增更多任务 Bean。
