# D. 关键类说明（20个）

1. `NsbhApplication`：应用启动入口，启用配置绑定与调度。
2. `ConversationController`：会话创建/聊天/消息查询 API。
3. `ToolsController`：工具元数据查询 API。
4. `GlobalExceptionHandler`：统一异常响应模型。
5. `RequestIdFilter`：每个请求生成/透传 `requestId`，写入 MDC 与响应头。
6. `RequestIdSupport`：在控制器返回体中读取当前 `requestId`。
7. `ConversationService`：聊天主编排、记忆窗口、摘要压缩、消息持久化。
8. `LlmClient`：LLM 抽象接口（首轮回复、最终回复、摘要）。
9. `MockLlmClient`：测试/本地默认 LLM，实现确定性工具调用。
10. `OpenAiLlmClient`：OpenAI 实现，含超时与错误映射。
11. `ToolService`：工具执行总控（注册、权限、限制、审计）。
12. `ToolRegistry`：扫描 `@NsbhTool` 自动注册工具并提供元数据。
13. `TimeTool`：返回当前服务器时间。
14. `HttpGetTool`：HTTP 拉取工具，含 SSRF 基础防护、超时和输出限制。
15. `DailySummaryScheduler`：按 cron 对近 24h 会话生成 `DAILY_SUMMARY`。
16. `NsbhProperties`：`nsbh.*` 配置绑定（LLM、memory、tools、permissions）。
17. `ConversationEntity`：会话实体（id、createdAt、updatedAt）。
18. `MessageEntity`：消息实体（role/type/content/tool字段/时间）。
19. `ConversationRepository`：会话 CRUD。
20. `MessageRepository`：消息查询统计（窗口、类型、时间范围、汇总查询）。
