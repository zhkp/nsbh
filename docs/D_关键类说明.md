# D. 关键类说明（20个）

1. `NsbhApplication`：应用启动入口，启用配置绑定与调度。
2. `ConversationController`：`/conversations` 相关 WebFlux 接口。
3. `ToolsController`：`/tools` 元数据接口。
4. `GlobalExceptionHandler`：统一异常到 API 错误响应。
5. `RequestIdFilter`：WebFilter 注入/透传 `requestId`，并写入 Reactor Context 与 MDC。
6. `RequestIdSupport`：从 `ServerWebExchange` 读取当前 `requestId`。
7. `ConversationService`：聊天主编排、记忆窗口、摘要压缩、消息持久化。
8. `LlmClient`：LLM 抽象（`firstReply/finalReply/summarize`）。
9. `MockLlmClient`：默认 mock provider，测试可预测。
10. `OpenAiLlmClient`：OpenAI WebClient 实现，支持 timeout 与错误映射。
11. `Tool`：工具统一接口（`Mono<String> execute(...)`）。
12. `NsbhTool`：工具元注解（name/description/schema/requiredPermissions）。
13. `ToolRegistry`：自动发现并注册工具，输出 `ToolMetadata`。
14. `ToolService`：工具执行治理和 `TOOL_AUDIT` 审计日志。
15. `TimeTool`：返回服务器当前时间。
16. `HttpGetTool`：带 SSRF 防护与响应大小限制的 HTTP GET 工具。
17. `ConversationEntity`：会话表映射（R2DBC）。
18. `MessageEntity`：消息表映射（R2DBC）。
19. `ReactiveEntityCallbacks`：保存前自动补 id/时间戳/默认类型。
20. `DailySummaryScheduler`：按 cron 生成并持久化 `DAILY_SUMMARY`。
