# G. 项目风险地图

## 1. 潜在并发问题
- `ToolService` 使用 `CompletableFuture.supplyAsync`（公共线程池），在高并发下可能被工具执行占满。
- `ConversationService.chat` 同一会话并发写入时，消息顺序依赖数据库提交时序，可能出现“用户预期顺序”与实际略有偏差。
- scheduler 与实时 chat 同时写同一会话消息，虽事务隔离能保证一致性，但摘要时点可能与聊天时点交错。

## 2. 可能的性能瓶颈
- `buildPromptWindow` 目前多次查询消息（SUMMARY + NORMAL），会在长会话和高 QPS 下放大数据库压力。
- `OpenAiLlmClient` 每次请求都完整构建消息数组，历史较长时对象创建开销增加。
- `http_get` 工具若频繁访问慢站点，会占用工具执行线程与请求时延预算。

## 3. 代码结构不合理点
- DTO、业务模型、LLM 协议对象目前都在单体模块中，边界清晰但仍偏“单包大类”，可进一步分包（如 `llm/openai`）。
- `ConversationService` 体量偏大（流程、记忆策略、消息构造都在一个类），维护成本会上升。
- 部分日志 JSON 是“message 内嵌 JSON 字符串”，可演进为字段直出（encoder provider 定制）。

## 4. 可重构部分
- 将 `ConversationService` 拆分：
  - `PromptBuilder`
  - `MemoryCompactionService`
  - `ChatOrchestrator`
- 将工具执行从 `supplyAsync` 改为专用 `Executor`（可配置线程数与队列）。
- 为 `MessageRepository` 增加更聚合的“单次查询返回所需窗口”的方法，减少 round trips。
- 引入结构化日志统一字段模型（避免各处手动拼 JSON）。
