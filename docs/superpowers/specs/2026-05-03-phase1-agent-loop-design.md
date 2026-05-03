# Phase 1 Design：真正的 Agent Loop + 流式输出 + 多 Provider

**日期**：2026-05-03  
**分支**：feature_webflux  
**状态**：已批准，待实现

---

## 目标

让 NSBH 具备真正的 agentic 能力：

1. 多步工具调用循环（agent loop）
2. 并行工具执行
3. SSE 流式输出（含工具进度事件）
4. 多 LLM Provider（OpenAI-compatible / Anthropic / Ollama）
5. `ConversationService` 拆分重构

---

## 核心数据模型

### `AgentEvent`（新建）

密封接口，描述 agent loop 产生的所有事件：

```java
public sealed interface AgentEvent permits
    AgentEvent.TextDelta,
    AgentEvent.ToolStart,
    AgentEvent.ToolEnd,
    AgentEvent.Done {

    record TextDelta(String text) implements AgentEvent {}
    record ToolStart(String toolName, String toolCallId) implements AgentEvent {}
    record ToolEnd(String toolName, String toolCallId,
                   ToolCallStatus status, String result) implements AgentEvent {}
    record Done(String fullText, List<ToolExecutionResult> toolResults) implements AgentEvent {}
}
```

### `LlmReply`（改造）

```java
// 改前
public record LlmReply(String assistantMessage, ToolCallRequest toolCall) {}

// 改后
public record LlmReply(String assistantMessage, List<ToolCallRequest> toolCalls) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
```

### `NsbhProperties` 新增

```yaml
nsbh:
  agent:
    max-tool-rounds: 10   # 防止死循环，默认 10
```

---

## 类结构

### 新增 / 改造文件

```
agent/
├── AgentEvent.java           新建：密封事件接口
├── ChatOrchestrator.java     新建：agent loop，返回 Flux<AgentEvent>
├── PromptBuilder.java        新建：prompt window 组装
├── MemoryService.java        新建：summary compaction
├── ConversationService.java  保留：精简为薄门面（< 60 行）
├── LlmClient.java            扩展：增加 streamFirstReply
├── LlmReply.java             改造：单工具 → List<ToolCallRequest>
├── OpenAiLlmClient.java      改造：多工具收集 + streaming
├── AnthropicLlmClient.java   新建：Anthropic provider
├── MockLlmClient.java        改造：支持脚本化多轮预设
└── ToolCallRequest.java      不变
```

### 职责边界

| 类 | 输入 | 输出 | 依赖 |
|----|------|------|------|
| `ChatOrchestrator` | conversationId, userMessage, model | `Flux<AgentEvent>` | LlmClient, ToolService, PromptBuilder, MemoryService, MessageRepository |
| `PromptBuilder` | conversationId | `Mono<List<MessageEntity>>` | MessageRepository, NsbhProperties |
| `MemoryService` | conversationId, model | `Mono<Void>` | MessageRepository, LlmClient, NsbhProperties |
| `ConversationService` | conversationId, userMessage, model | `Mono<ChatResult>` | ChatOrchestrator, ConversationRepository |

### `LlmClient` 接口

```java
public interface LlmClient {
    Mono<LlmReply> firstReply(String userMessage, String model,
                               List<MessageEntity> window);
    Mono<String> finalReply(String userMessage, String model,
                             String toolResult, List<MessageEntity> window);
    Flux<String> streamFirstReply(String userMessage, String model,   // 新增
                                   List<MessageEntity> window);
    Mono<String> summarize(List<MessageEntity> messages, String model);
}
```

---

## Agent Loop

### 流程

```
orchestrate(conversationId, userMessage, model) → Flux<AgentEvent>:

  1. save USER message
  2. maybeCompact(conversationId, model)
  3. loop(round=0, accumulatedResults=[])

loop(round, accumulatedResults):
  if round >= maxToolRounds:
    emit Done("", accumulatedResults)
    return

  buildPromptWindow(conversationId)
  → llmClient.firstReply(userMessage, model, window)

  if reply.hasToolCalls() == false:
    emit TextDelta chunks via streamFirstReply(...)
    save ASSISTANT message
    emit Done(fullText, accumulatedResults)
    return

  if reply.hasToolCalls():
    for each toolCall → emit ToolStart(toolName, toolCallId)
    Flux.mergeDelayError(toolCalls.map(tc → toolService.execute(tc)))
      → for each result:
          save TOOL message to DB
          emit ToolEnd(toolName, toolCallId, status, result)
      → collectList()
      → loop(round + 1, accumulatedResults + results)  // 递归
```

### 关键实现决策

- **中间轮次**（有工具调用时）：用 `firstReply`（批量，等工具结果）
- **最后一轮**（无工具调用时）：用 `streamFirstReply` 推文字 delta
- **并行执行**：`Flux.mergeDelayError`，一个工具失败不中断其他
- **递归安全**：`Mono.defer(() -> loop(...)).flatMapMany(...)`，Reactor trampolining 防栈溢出
- **DB 消息**：每条 TOOL 消息含 `toolCallId`，与 OpenAI tool_call_id 对应

---

## API 端点

### 现有端点（不变）

```
POST /conversations/{id}/chat
→ chatOrchestrator.orchestrate(...)
    .collectList()
    .map(events → extractChatResult(events))
```

### 新增流式端点

```
POST /conversations/{id}/chat/stream
Content-Type: application/json
Accept: text/event-stream

→ chatOrchestrator.orchestrate(...)
    .map(event → toServerSentEvent(event))
```

### SSE 事件格式

```
event: text_delta
data: {"text":"你好"}

event: tool_start
data: {"toolName":"time","toolCallId":"call_abc"}

event: tool_end
data: {"toolName":"time","toolCallId":"call_abc","status":"SUCCESS","result":"..."}

event: done
data: {"fullText":"现在是...","toolCount":1}
```

---

## 多 Provider

### OpenAI-compatible（改造）

改动点：
- `firstReply()`：收集 `toolCalls` 列表全部元素，不再只取 `get(0)`
- 新增 `streamFirstReply()`：请求加 `"stream": true`，`bodyToFlux(String.class)` 接收，解析 `delta.content`

### Anthropic（新建）

格式差异：

| 项 | OpenAI | Anthropic |
|----|--------|-----------|
| Auth | `Authorization: Bearer {key}` | `x-api-key: {key}` + `anthropic-version: 2023-06-01` |
| System prompt | messages 里 role=system | 顶层字段 `"system": "..."` |
| 工具调用响应 | `message.tool_calls[]` | `content[]` type=`tool_use` |
| 工具结果输入 | role=`tool` + `tool_call_id` | role=`user` + content type=`tool_result` |
| Stream delta | `choices[0].delta.content` | `content_block_delta.delta.text` |

`@ConditionalOnProperty(prefix = "nsbh.llm", name = "provider", havingValue = "anthropic")`

### Ollama（新建）

复用 OpenAI 逻辑，差异：
- 默认 `base-url: http://localhost:11434`
- 无需 `api-key` 非空校验
- `@ConditionalOnProperty(havingValue = "ollama")`

### 验收用配置（OpenRouter 免费模型）

```yaml
nsbh:
  llm:
    provider: openai
    model-default: meta-llama/llama-3.3-70b-instruct:free
    base-url: https://openrouter.ai/api
    api-key: ${OPENROUTER_API_KEY:}
    timeout-ms: 30000
```

---

## 测试策略

**方式**：TDD——先写新测试（红），再写实现（绿），旧测试在对应新测试绿后删除。

### 新增测试

| 文件 | 验证内容 |
|------|---------|
| `ChatOrchestratorTest` | loop 轮次、工具并行、`maxToolRounds` 截止、事件序列 |
| `PromptBuilderTest` | summary + normal window 组装 |
| `MemoryServiceTest` | compaction 触发条件、SUMMARY 写入 |
| `AgentLoopIntegrationTest` | MockLlmClient 跑 3 轮工具调用，验证 DB 记录 + 事件流 |
| `SseStreamIntegrationTest` | WebTestClient 订阅 `/chat/stream`，验证 SSE 事件格式 |
| `OpenAiMultiToolTest` | mock WebServer 验证多工具收集，不丢弃 |
| `AnthropicLlmClientTest` | mock WebServer 验证 Anthropic 格式转换 |
| `ProviderSwitchTest` | 三种 provider 各自 Bean 装载正确 |

### `MockLlmClient` 改造

支持脚本化预设，模拟多轮：

```java
mockLlmClient.script(
    LlmReply.withTools(List.of(new ToolCallRequest("id1", "time", "{}"))),
    LlmReply.withTools(List.of(new ToolCallRequest("id2", "time", "{}"))),
    LlmReply.text("最终回复")
);
```

### 旧测试删除时机

`ConversationServiceToolPathTest`、`ConversationServicePromptWindowTest`、`SummaryCompactionIntegrationTest` 等，在对应新测试全绿后删除。

---

## 验收标准

- [ ] `mvn test` 全绿
- [ ] MockLlmClient 脚本模拟 3 轮工具调用，`ChatOrchestrator` 正确循环，DB 消息记录完整
- [ ] `POST /conversations/{id}/chat/stream` 返回 SSE 流，事件顺序：`tool_start` → `tool_end` → `text_delta` × N → `done`
- [ ] 配置 `provider: anthropic` Integration 测试（mock WebServer）通过
- [ ] 配置 OpenRouter（`meta-llama/llama-3.3-70b-instruct:free`）能完成一轮含工具调用的真实对话
- [ ] `ConversationService` 行数 < 60 行
