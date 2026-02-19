# A. 项目整体架构说明

## 1. 项目整体架构说明
本项目是单体 Spring Boot 应用，采用端到端 Reactive 架构：
- 接入层：WebFlux Controller（`api`）
- 业务编排层：对话流程、LLM、记忆压缩（`agent`）
- 工具层：工具注册、策略校验、执行审计（`tools`）
- 数据层：R2DBC 实体与仓储（`memory.entity`、`memory.repo`）
- 调度层：每日摘要任务（`scheduler`）
- 基础设施层：配置、日志、requestId（`config`、`logging`、`api.RequestId*`）

核心运行链路：
`Controller (Mono/Flux) -> ConversationService -> (LlmClient + ToolService) -> Reactive Repository -> DB`

## 2. 模块职责划分
- `com.kp.nsbh.api`：HTTP API、DTO、统一错误模型、requestId 注入与透传。
- `com.kp.nsbh.agent`：聊天主流程编排、Prompt 窗口、summary compaction、LLM 适配。
- `com.kp.nsbh.tools`：`@NsbhTool` 注册、allowlist/permission/timeout/size 控制、TOOL_AUDIT。
- `com.kp.nsbh.memory.entity`：会话与消息数据模型（R2DBC 映射）。
- `com.kp.nsbh.memory.repo`：ReactiveCrudRepository + 查询方法。
- `com.kp.nsbh.scheduler`：daily summary 定时任务（可开关）。
- `com.kp.nsbh.config`：`nsbh.*` 配置绑定与 schema 初始化。
- `com.kp.nsbh.logging`：结构化日志字段拼装。

## 3. 各模块之间的依赖关系
- `api -> agent`
- `agent -> memory.repo + tools + config`
- `OpenAiLlmClient -> ToolRegistry`（动态输出工具定义）
- `tools -> config + logging`
- `scheduler -> memory.repo + agent.LlmClient + config`
- `memory.repo -> memory.entity`

## 4. 技术栈总结
- Java 21
- Spring Boot 3.3.4
- Spring WebFlux（`spring-boot-starter-webflux`）
- Spring Data R2DBC（H2/PostgreSQL）
- JDBC + Flyway（迁移 sidecar）
- WebClient（OpenAI 调用）
- Spring Scheduling
- springdoc-openapi-webflux-ui
- Logback + logstash-logback-encoder（JSON 日志）
- JUnit5 + Spring Boot Test + Testcontainers

## 5. 典型请求执行流程
以 `POST /api/v1/conversations/{id}/chat` 为例：
1. `ConversationController.chat` 收到请求，读取 `requestId`。
2. `ConversationService.chat` 校验会话并写入 USER 消息。
3. 执行 `maybeCompactMemory`：超过阈值时调用 `LlmClient.summarize`，写入 `SUMMARY`。
4. 构建 Prompt：`system prompt + latest summary + last N normal messages`。
5. 调 `LlmClient.firstReply`：
   - 无工具调用：直接写 ASSISTANT 消息并返回。
   - 有工具调用：进入 `ToolService.execute`，完成策略校验与执行。
6. 工具结果写入 TOOL 消息，调用 `LlmClient.finalReply` 生成最终回答。
7. 写入 ASSISTANT 消息，返回 `assistantMessage + toolCalls + requestId`。
