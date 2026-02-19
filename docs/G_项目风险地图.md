# G. 项目风险地图

## 1. 潜在并发问题
- 同一 `conversationId` 并发 `chat` 时，消息顺序依赖提交时序，可能与用户感知顺序有偏差。
- `DailySummaryScheduler` 与实时 chat 同时写消息时，摘要内容存在时间窗口竞争（最终一致，不是强一致快照）。
- `HttpGetTool` 在 `boundedElastic` 执行阻塞 I/O，高并发时会占用弹性线程池容量。

## 2. 可能的性能瓶颈
- `ConversationService` 多处 `findByConversationIdOrderByCreatedAtAsc(...).collectList()`，长会话下会带来内存与 DB 压力。
- summary compaction 每次触发都扫描全量 NORMAL 消息，数据量大时成本较高。
- OpenAI 与外部工具调用都受网络影响，容易成为请求延迟主因。

## 3. 代码结构不合理点
- `ConversationService` 同时承担流程编排、prompt 组装、记忆压缩，职责偏重。
- `OpenAiLlmClient` 内部 request/response record 与映射逻辑较集中，后续扩展多 provider 时可拆分。
- `R2dbcSchemaInitConfig` 与 Flyway 并存，初始化职责边界可进一步明确。

## 4. 可重构部分
- 将 `ConversationService` 拆分：
  - `PromptBuilder`
  - `MemoryCompactionService`
  - `ChatOrchestrator`
- 为 prompt window 增加更聚合的 repository 查询，减少全量消息拉取。
- 为工具执行引入专用 `Scheduler/Executor` 与限流策略。
- 统一结构化日志字段输出，减少 message 中嵌套 JSON 字符串。
