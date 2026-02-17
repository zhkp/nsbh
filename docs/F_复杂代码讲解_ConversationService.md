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
可以把它理解为一个“对话流水线调度器”：

1. **先确认会话存在**
- 如果会话不存在，直接 404。

2. **把用户这句话存下来**
- 任何后续决策都基于数据库里的消息历史。

3. **看要不要压缩记忆**
- 当 NORMAL 消息太多（超过 `compactAfter`）时，调用 LLM 生成摘要。
- 摘要作为 `SYSTEM + SUMMARY` 消息保存，后续构建 Prompt 会用它减少上下文长度。

4. **组装 Prompt 窗口**
- 固定系统提示 + 最新摘要（如果有）+ 最近 N 条 NORMAL 消息。
- 这让上下文既保留关键事实，也控制 token。

5. **第一次问 LLM**
- LLM 可能直接回答，也可能要求调用工具（如 `time`、`http_get`）。

6. **如果要调用工具**
- 把请求交给 `ToolService`。
- `ToolService` 会做 allowlist、权限、超时、输入输出大小限制。
- 执行结果保存为 TOOL 消息，再次调用 LLM 生成最终自然语言答复。

7. **保存助手最终回复并返回**
- 保存 ASSISTANT 消息。
- 返回 `assistantMessage` 和 `toolCalls` 给前端。

## 为什么这个实现重要
- 把“模型调用、工具调用、记忆持久化”三件复杂事串成一个稳定流程。
- 通过事务边界和统一入口，降低状态不一致风险。
- 为后续扩展多工具、多模型、多轮代理循环打下基础。

## 对应代码位置
- `src/main/java/com/kp/nsbh/agent/ConversationService.java`
