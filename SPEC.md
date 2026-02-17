  # NSBH WebFlux SPEC（Complete Reactive Edition）

  ## 0. Goal

  将当前 NSBH 从 Spring MVC + JPA 架构迁移为 端到端 Reactive 架构：

  - API 层使用 Spring WebFlux
  - 数据层使用 R2DBC（替代 JPA）
  - LLM/工具/调度链路全部非阻塞
  - 保持现有功能与安全模型
  - 提升并发能力与资源利用率

  ## 1. Non-goals

  - 不做跨服务拆分（仍为单体）
  - 不做多租户、分布式事务
  - 不在 v1 强制引入事件总线
  - 不做 UI 改造

  ## 2. Tech Stack

  - Java 21
  - Spring Boot 3.x + Spring WebFlux
  - Reactor (Mono, Flux)
  - Spring Data R2DBC
  - R2DBC driver:
      - H2: r2dbc-h2（开发）
      - PostgreSQL: r2dbc-postgresql（生产）
  - Flyway（JDBC sidecar，仅做 schema migration）
  - WebClient（LLM HTTP）
  - springdoc-openapi（WebFlux starter）
  - Test: JUnit5 + WebTestClient + Testcontainers

  ## 3. Architecture Principles

  1. 端到端 non-blocking，禁止在主链路调用 .block()。
  2. 所有 I/O（DB/HTTP/工具网络）必须是 reactive。
  3. 阻塞代码必须显式隔离（如必须存在，放 boundedElastic 并标记 TODO）。
  4. 错误模型与 requestId 语义保持兼容。
  5. 安全策略（tools allowlist/permission/timeout/size limit）保持不降级。

  ## 4. Domain Model

  与现有保持一致：

  ### 4.1 Entities

  - Conversation
      - id (UUID)
      - createdAt, updatedAt
  - Message
      - id (UUID)
      - conversationId
      - role: SYSTEM | USER | ASSISTANT | TOOL
      - type: NORMAL | SUMMARY | DAILY_SUMMARY
      - content
      - toolName (nullable)
      - toolCallId (nullable)
      - createdAt

  ### 4.2 Repository（Reactive）

  - ReactiveConversationRepository
  - ReactiveMessageRepository
  - 返回类型统一 Mono<T> / Flux<T>

  ## 5. API Design (Reactive)

  Base path: /api/v1（保持不变）

  - POST /conversations
  - POST /conversations/{id}/chat
  - GET /conversations/{id}/messages
  - GET /tools

  Controller 返回：

  - 单对象：Mono<ResponseDto>
  - 列表：Flux<Dto> 或 Mono<List<Dto>>（建议 Flux）

  ## 6. LLM Provider (Reactive)

  定义 ReactiveLlmClient：

  - Mono<LlmReply> firstReply(...)
  - Mono<String> finalReply(...)
  - Mono<String> summarize(...)

  实现：

  - MockReactiveLlmClient
  - OpenAiReactiveLlmClient（WebClient，全链路无 block）

  配置：

  - nsbh.llm.provider=mock|openai
  - nsbh.llm.apiKey=${OPENAI_API_KEY:}
  - nsbh.llm.timeoutMs

  要求：

  - 错误映射明确（401/429/5xx/timeout）
  - 不打印 secrets

  ## 7. Tools (Reactive)

  定义 ReactiveTool：

  - Mono<String> execute(String inputJson)

  工具：

  1. time（即时返回）
  2. http_get（Reactive HTTP client；SSRF 防护、timeout、max output）

  工具执行服务 ReactiveToolService：

  - allowlist 检查
  - permission 检查
  - input/output bytes 限制
  - timeout（timeout(Duration)）
  - 审计日志（TOOL_AUDIT）

  ## 8. Memory & Prompt

  - memory.window
  - memory.compactAfter
  - Prompt 构建：
      - system prompt
      - latest SUMMARY
      - last N NORMAL messages
  - 超阈值触发 summary compaction：
      - 调 ReactiveLlmClient.summarize
      - 写入/更新 SUMMARY

  ## 9. Scheduler (Reactive)

  - scheduler.dailySummary.enabled=false
  - scheduler.dailySummary.cron=...
  - 调度任务：
      - 找最近 24h 活跃会话
      - summarize
      - 写 DAILY_SUMMARY
  - 生成 jobRunId
  - 日志 JSON 输出

  ## 10. Observability

  - 全局 JSON log（保留）
  - requestId（WebFlux filter + Reactor Context + MDC bridge）
  - TOOL_AUDIT 固定键：
      - requestId, conversationId, toolName, status, reason, durationMs

  ## 11. Security Requirements

  - Default deny tool execution
  - 强制：
      - nsbh.tools.allowed
      - nsbh.permissions.granted
  - SSRF 防护不可回退
  - 禁止 shell tool（v1）

  ## 12. Data & Migration

  - Flyway 使用 JDBC DataSource 做 schema migration
  - 业务读写走 R2DBC
  - H2 开发 + Postgres profile
  - schema 与现有版本兼容

  ## 13. Testing (MUST)

  ### 13.1 Unit

  - Reactive tool permission enforcement
  - timeout / output limit
  - prompt window selection
  - provider switch

  ### 13.2 Integration

  - WebTestClient 验证 chat API 200
  - message persistence
  - requestId presence
  - tool rejection reason mapping
  - summary compaction
  - scheduler manual invoke

  ### 13.3 Postgres

  - Testcontainers + R2DBC + Flyway
  - 无 Docker 时 skip（可配置）

  ## 14. Milestones & Acceptance

  ### Milestone W1 — Reactive Baseline

  Deliver:

  - WebFlux controllers
  - Reactive service skeleton
  - MockReactiveLlmClient
  - time tool reactive
    Acceptance:
  - mvn test passes
  - chat basic flow works

  ### Milestone W2 — Reactive Persistence

  Deliver:

  - R2DBC repositories replacing JPA
  - Flyway + schema validated
  - H2/Postgres profiles
    Acceptance:
  - messages persisted correctly
  - Postgres Testcontainers test pass/skip as expected

  ### Milestone W3 — Reactive Tooling & LLM

  Deliver:

  - OpenAiReactiveLlmClient (no block)
  - ReactiveToolService with allowlist/permission/timeout/size
  - http_get reactive with SSRF guard
    Acceptance:
  - tool rejection reasons correct
  - openai/mock switch works

  ### Milestone W4 — Memory & Scheduler

  Deliver:

  - summary compaction reactive
  - daily summary scheduler
  - JSON logging + requestId context in reactive chain
    Acceptance:
  - compactAfter trigger works
  - scheduler enabled/disabled behavior correct
  - all tests pass

  ## 15. Repo Layout (target)

  - api/ (WebFlux controllers, dto, error)
  - agent/ (reactive orchestration, llm interfaces)
  - tools/ (reactive tools + registry + executor)
  - memory/ (entities, r2dbc repositories)
  - scheduler/ (jobs)
  - config/, logging/
  - test/ (WebTestClient, Testcontainers)

  ## 16. Migration Constraints

  1. 每次里程碑必须保持可运行、可回归（mvn test 绿）。
  2. 不允许引入 .block() 到业务主路径。
  3. 先保持 API contract 不变，再做内部替换。
  4. 先 Mock 跑通，再接 OpenAI，再接 scheduler。

  ## 17. Verify Checklist

  - mvn test
  - curl 基础聊天
  - /api/v1/tools 包含 time / http_get
  - allowlist/permission 拒绝路径
  - requestId in response
  - JSON logs visible
  - Postgres profile + Testcontainers (if docker available)