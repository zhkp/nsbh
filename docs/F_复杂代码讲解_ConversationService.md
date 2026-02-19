# F. 复杂代码讲解（`ConversationService`）

## 这个类解决了什么问题
`ConversationService` 解决的是“把一次聊天请求真正跑通”的问题：
- 用户消息怎么入库
- 何时调用 LLM
- 工具调用是否需要执行
- 如何把工具结果再喂回 LLM
- 如何保存完整上下文（USER/TOOL/ASSISTANT/SUMMARY）

它是整个项目的业务中枢。

## 用通俗语言解释实现逻辑
可以把它理解成一个“Reactive 对话流水线调度器”：

1. **先确认会话存在**
- 如果会话不存在，直接 404。

2. **把用户这句话存下来**
- 先写入 `NORMAL + USER`，后续步骤都基于持久化历史。

3. **看要不要压缩记忆**
- 当 NORMAL 消息超过 `compactAfter`，调用 `LlmClient.summarize(...)` 生成摘要。
- 删除旧 SUMMARY 并写入新 `SYSTEM + SUMMARY` 消息，降低上下文长度。

4. **组装 Prompt 窗口**
- 固定系统提示 + 最新摘要（如果有）+ 最近 N 条 NORMAL 消息。
- 保留关键上下文，同时控制 token 消耗。

5. **第一次问 LLM**
- LLM 可能直接回答，也可能要求调用工具（如 `time`、`http_get`）。

6. **如果要调用工具**
- 把请求交给 `ToolService`。
- `ToolService` 会做 allowlist、权限、超时、输入输出大小限制。
- 执行结果写为 TOOL 消息，再次调 LLM 生成最终答复。

7. **保存助手最终回复并返回**
- 保存 ASSISTANT 消息。
- 返回 `assistantMessage + toolCalls`（Controller 再附加 `requestId`）。

## 为什么这个实现重要
- 把模型、工具、持久化三条链路收敛到一个稳定入口。
- 通过 `Mono/Flux` 串联异步步骤，避免主链路阻塞。
- 为后续扩展更多工具与多轮代理提供统一骨架。

## 对应代码位置
- `src/main/java/com/kp/nsbh/agent/ConversationService.java`
