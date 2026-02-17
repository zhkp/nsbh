# A. 项目整体架构说明

## 1. 项目整体架构说明
本项目是一个基于 Spring Boot 的单体应用，采用典型分层架构：
- 接入层：REST API（`api` 包）
- 业务层：会话编排、LLM 调用、工具执行（`agent`、`tools`、`scheduler`）
- 数据层：JPA 实体与仓储（`memory.entity`、`memory.repo`）
- 基础设施层：配置、日志、请求追踪（`config`、`logging`、`api.RequestId*`）

运行时核心路径：
`Controller -> ConversationService -> (LlmClient + ToolService) -> Repository -> DB`

## 2. 模块职责划分
- `com.kp.nsbh.api`：提供 HTTP 接口、DTO 映射、异常统一返回、requestId 注入。
- `com.kp.nsbh.agent`：实现对话主流程、Prompt 构建、LLM 提供方适配（mock/openai）。
- `com.kp.nsbh.tools`：工具定义、注册发现、权限与超时/大小控制、审计日志。
- `com.kp.nsbh.memory.entity`：`Conversation/Message` 数据模型。
- `com.kp.nsbh.memory.repo`：会话与消息查询、统计与分页窗口查询。
- `com.kp.nsbh.scheduler`：每日摘要任务（开关+cron）。
- `com.kp.nsbh.config`：统一配置绑定（`nsbh.*`）。
- `com.kp.nsbh.logging`：JSON 日志辅助。

## 3. 各模块之间的依赖关系
- `api` 依赖 `agent`（调用业务服务），依赖 `api.RequestIdSupport`（返回 requestId）。
- `agent` 依赖 `memory.repo`、`tools`、`config`。
- `agent.OpenAiLlmClient` 依赖 `tools.ToolRegistry`（动态工具定义）。
- `tools` 依赖 `config`、`logging`。
- `scheduler` 依赖 `agent.LlmClient` + `memory.repo` + `config`。
- `memory.repo` 依赖 `memory.entity`。

## 4. 技术栈总结
- 语言/框架：Java 21, Spring Boot 3.3.4
- Web：Spring MVC (`spring-boot-starter-web`)
- 校验：Bean Validation
- 数据访问：Spring Data JPA
- 数据库：H2（默认）+ PostgreSQL（profile）
- 迁移：Flyway
- LLM 调用：Spring WebClient（OpenAI Chat Completions）
- 定时任务：Spring Scheduling
- 文档：springdoc-openapi
- 日志：Logback + logstash-logback-encoder（JSON）
- 测试：JUnit5 + Spring Boot Test + Testcontainers

## 5. 典型请求执行流程
以 `POST /api/v1/conversations/{id}/chat` 为例：
1. `ConversationController.chat` 接收请求并交给 `ConversationService.chat`。
2. `ConversationService` 保存 USER 消息，必要时触发摘要压缩。
3. 构建 Prompt（system + latest summary + last N normal messages）。
4. 调用 `LlmClient.firstReply`，若返回 toolCall：
   - 进入 `ToolService.execute`，做注册检查、allowlist、权限、输入大小、超时、输出大小控制。
   - 执行工具并记录 `TOOL_AUDIT`。
   - 保存 TOOL 消息。
   - 再调用 `LlmClient.finalReply`。
5. 保存 ASSISTANT 消息，返回 `assistantMessage + toolCalls + requestId`。
