# K. 对标 nanobot 演进路线图

> 参考项目：[HKUDS/nanobot](https://github.com/HKUDS/nanobot)
> 文档版本：2026-05
> 当前基线：NSBH WebFlux + R2DBC（feature_webflux 分支）

---

## 0. 背景与目标

nanobot 是一个 Python 实现的生产级 AI Agent 平台，核心特性包括：真正的 agentic loop、多渠道接入（Telegram/Discord/Slack/微信/飞书等）、MCP 协议工具扩展、两阶段记忆系统、Skills 插件体系、WebUI、Docker 部署、可观测性。

NSBH 当前是一个验证 Spring WebFlux + R2DBC 完整响应式链路的工程习题，与 nanobot 的主要差距如下：

| 维度 | NSBH 现状 | nanobot |
|------|-----------|---------|
| Agent Loop | 固定 `firstReply→单次工具→finalReply` | 真正的多步 agentic loop，支持并行多工具 |
| 流式输出 | 无，完整生成后返回 | 端到端 SSE streaming |
| LLM Provider | mock / OpenAI | 15+ providers（Anthropic、DeepSeek、Ollama、Azure 等） |
| 工具数量 | `time`、`http_get` | 内置丰富工具 + MCP 协议接无限第三方 |
| 渠道 | 仅 REST API | Telegram、Discord、Slack、WeChat、Feishu 等 12+ 渠道 |
| 记忆 | 消息条数窗口 + 简单压缩 | Token 计数 + 两阶段（工作记忆+长期记忆）|
| Skills | 无 | YAML 定义技能 + ClawHub 市场 |
| WebUI | 无 | WebSocket + React UI |
| 部署 | Spring Boot 启动 | Docker、Linux service、macOS LaunchAgent |
| 可观测性 | JSON 日志 + requestId | Micrometer、Langfuse、Prometheus |

**演进策略**：由内到外，先修 Agent Core（一切的地基），再扩工具生态，再接渠道，最后生产化。每期结束必须保持 `mvn test` 全绿。

---

## 1. 第一期：真正的 Agent Loop + 流式输出 + 多 Provider

**目标**：让 NSBH 具备真正的 agentic 能力。这是后续所有功能的基础，必须最先完成。

### 1.1 现状问题分析

**问题一：单步工具调用写死**

`ConversationService.chat()` → `executeAndReply()` 路径：

```
firstReply()
  └─ if toolCall → executeTool() → finalReply()   ← 只能调一次工具，然后强制结束
  └─ if no tool  → 直接返回
```

真实场景中，LLM 完成一个任务可能需要连续调用 5~10 次工具（如先搜索，再读文件，再写文件，再验证结果）。现在的结构完全无法支持。

**问题二：`LlmReply` 只持有单个工具调用**

```java
// agent/LlmReply.java（当前）
public record LlmReply(String assistantMessage, ToolCallRequest toolCall) {}
```

OpenAI 等模型在一次响应中可以请求调用多个工具（parallel function calling），当前结构会丢弃除第一个以外的所有工具调用。

`OpenAiLlmClient.firstReply()` 第 62~70 行：
```java
ChatCompletionsToolCall call = toolCalls.get(0);  // ← 只取第 0 个，其余全部丢弃
```

**问题三：`ConversationService` 职责过重**

单个类同时负责：会话 CRUD、prompt 组装、记忆压缩、工具编排、LLM 调用路由，超过 240 行，难以测试和扩展。

**问题四：无流式输出**

`LlmClient` 接口只有批量响应方法，无 streaming。用户必须等 LLM 完整生成（可能 10~30 秒）才能看到回复。

**问题五：Provider 只有 mock/OpenAI**

`OpenAiLlmClient` 硬编码 OpenAI 格式，无法接 Anthropic（不同的 request/response 结构）或本地 Ollama。

---

### 1.2 架构重构：ConversationService 拆分

**拆分目标**：

```
agent/
├── ChatOrchestrator.java       ← Agent loop 主流程（新建）
├── PromptBuilder.java          ← System prompt + summary + window 组装（新建）
├── MemoryService.java          ← Summary compaction 逻辑（从 ConversationService 提取）
├── ConversationService.java    ← 退化为薄门面：参数校验 + 事务入口（保留，精简）
├── LlmClient.java              ← 接口扩展（增加 stream 方法）
├── LlmReply.java               ← 改为持有 List<ToolCallRequest>
└── ToolCallRequest.java        ← 不变
```

**`PromptBuilder`** 职责：
- `buildPromptWindow(conversationId)` —— 从 DB 拉消息，按 summary + normal window 规则组装
- `systemPromptMessage()` —— 生成 system 消息实体
- `assemblePrompt(summary, normals)` —— 拼接最终列表

**`MemoryService`** 职责：
- `maybeCompact(conversationId, model)` —— 检查 NORMAL 消息数量，超阈值时调 LLM summarize，写 SUMMARY

**`ChatOrchestrator`** 职责（核心新增）：
- 持有 `LlmClient`、`ToolService`、`PromptBuilder`、`MemoryService`、`MessageRepository`
- `orchestrate(conversationId, userMessage, model)` —— 实现 agent loop（见下节）

---

### 1.3 Agent Loop 实现

**循环逻辑（伪代码）**：

```
orchestrate(conversationId, userMessage, model):
  save USER message
  maybeCompact(conversationId)

  round = 0
  toolResults = []

  loop:
    if round >= maxToolRounds → break (防止死循环)

    promptWindow = buildPromptWindow(conversationId)
    reply = llmClient.firstReply(userMessage, model, promptWindow)

    if reply.toolCalls is empty:
      save ASSISTANT message
      return ChatResult(reply.assistantMessage, toolResults)

    // 并行执行所有工具调用
    currentRoundResults = Flux.merge(
      reply.toolCalls.map(tc → toolService.execute(tc))
    ).collectList().block()  ← 实际用 reactive flatMap

    for each result in currentRoundResults:
      save TOOL message

    toolResults.addAll(currentRoundResults)
    round++
```

**Reactor 实现方式**：

用 `Mono.expand()` 实现递归展开，或手写 `unfold` 风格的 `Flux`，保证全程 non-blocking：

```java
// ChatOrchestrator.java 核心方法骨架
private Mono<AgentRoundState> agentLoop(AgentRoundState state) {
    if (state.round() >= maxToolRounds || state.done()) {
        return Mono.just(state);
    }
    return buildPromptWindow(state.conversationId())
        .flatMap(window -> llmClient.firstReply(state.userMessage(), state.model(), window))
        .flatMap(reply -> {
            if (reply.toolCalls().isEmpty()) {
                return saveAssistant(state, reply.assistantMessage())
                    .thenReturn(state.withDone(true, reply.assistantMessage()));
            }
            // 并行执行所有工具
            return Flux.fromIterable(reply.toolCalls())
                .flatMap(tc -> toolService.execute(...))
                .flatMap(result -> saveToolMessage(state.conversationId(), result).thenReturn(result))
                .collectList()
                .map(results -> state.nextRound(results));
        })
        .flatMap(nextState -> agentLoop(nextState)); // 递归
}
```

**`AgentRoundState`** 是一个不可变 record，持有：`conversationId`、`userMessage`、`model`、`round`、`done`、`allToolResults`、`finalMessage`。

**配置新增**：

```yaml
nsbh:
  agent:
    max-tool-rounds: 10   # 防止死循环，默认 10
```

`NsbhProperties` 增加 `Agent` 内部类。

---

### 1.4 LlmReply 改造（支持多工具调用）

```java
// 改后
public record LlmReply(String assistantMessage, List<ToolCallRequest> toolCalls) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
```

`OpenAiLlmClient.firstReply()` 改为收集 `toolCalls` 列表中所有元素，而非只取 `get(0)`。

---

### 1.5 SSE 流式输出

**新增接口方法**：

```java
// LlmClient.java 增加
Flux<String> streamFirstReply(String userMessage, String model, List<MessageEntity> memoryWindow);
```

**`OpenAiLlmClient` 实现**：

OpenAI stream 格式是 `data: {"choices":[{"delta":{"content":"..."}}]}\n\n`，使用 `WebClient` 的 `bodyToFlux(String.class)` 接收，然后解析 delta：

```java
public Flux<String> streamFirstReply(String userMessage, String model, List<MessageEntity> window) {
    ChatCompletionsRequest request = new ChatCompletionsRequest(model, toMessages(window), ..., true); // stream=true
    return webClient.post()
        .uri("/v1/chat/completions")
        .bodyValue(request)
        .retrieve()
        .bodyToFlux(String.class)
        .filter(line -> line.startsWith("data: ") && !line.equals("data: [DONE]"))
        .map(line -> line.substring(6))  // 去掉 "data: "
        .mapNotNull(json -> extractDeltaContent(json));  // 解析 delta.content
}
```

**新增 Controller 端点**：

```java
// ConversationController.java 增加
@PostMapping(value = "/{id}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chatStream(@PathVariable UUID id, @RequestBody ChatRequest req) {
    return chatOrchestrator.streamChat(id, req.message(), req.model())
        .map(chunk -> ServerSentEvent.builder(chunk).build())
        .concatWith(Flux.just(ServerSentEvent.<String>builder().event("done").data("").build()));
}
```

原有 `POST /{id}/chat` 非流式端点保持不变，不破坏现有 API contract。

---

### 1.6 多 LLM Provider

**通用化 Provider 配置**：

```yaml
nsbh:
  llm:
    provider: openai          # openai | anthropic | ollama | deepseek | mock
    model-default: gpt-4.1-mini
    base-url: https://api.openai.com
    api-key: ${OPENAI_API_KEY:}
    timeout-ms: 15000
```

**开发与验收阶段推荐使用 OpenRouter 免费模型**，无需各平台单独申请 API key：

```yaml
nsbh:
  llm:
    provider: openai                          # OpenRouter 兼容 OpenAI 格式，直接复用
    model-default: meta-llama/llama-3.3-70b-instruct:free   # OpenRouter 免费模型
    base-url: https://openrouter.ai/api
    api-key: ${OPENROUTER_API_KEY:}
    timeout-ms: 30000                         # 免费模型延迟较高，适当放宽超时
```

常用 OpenRouter 免费模型（随时在 [openrouter.ai/models?q=free](https://openrouter.ai/models?q=free) 查最新列表）：
- `meta-llama/llama-3.3-70b-instruct:free`（支持工具调用）
- `google/gemini-2.0-flash-exp:free`（支持工具调用 + 视觉）
- `deepseek/deepseek-r1:free`（推理增强）
- `mistralai/mistral-7b-instruct:free`

DeepSeek、Moonshot、Kimi 等兼容 OpenAI 格式，只需修改 `base-url` 和 `api-key`，复用 `OpenAiLlmClient`（`provider: openai`）。

**新增 `AnthropicLlmClient`**：

Anthropic API 与 OpenAI 格式差异：
- URL：`https://api.anthropic.com/v1/messages`
- Auth header：`x-api-key: {apiKey}` + `anthropic-version: 2023-06-01`
- Request：`{"model":..., "messages":[...], "max_tokens":..., "tools":[...]}`
- Response：`{"content":[{"type":"text","text":"..."},{"type":"tool_use","id":"...","name":"...","input":{...}}]}`
- System prompt：独立的顶层字段，不放在 messages 里

```java
@Service
@ConditionalOnProperty(prefix = "nsbh.llm", name = "provider", havingValue = "anthropic")
public class AnthropicLlmClient implements LlmClient {
    // 独立实现 request/response 格式转换
    // content block 解析：type=text → assistantMessage，type=tool_use → ToolCallRequest
}
```

**新增 `OllamaLlmClient`**：

Ollama 提供 OpenAI-compatible endpoint（`/api/chat`），大部分逻辑可复用 `OpenAiLlmClient`，差异在于：
- 默认 `base-url: http://localhost:11434`
- 无需 `api-key`
- `ConditionalOnProperty(havingValue = "ollama")`

---

### 1.7 第一期验收标准

- [ ] `mvn test` 全绿
- [ ] Mock LLM 模拟 3 轮工具调用，`ChatOrchestrator` 正确循环执行并记录所有消息
- [ ] `POST /conversations/{id}/chat/stream` 返回 SSE 流，每个 chunk 独立到达
- [ ] 配置 `provider: anthropic` 能正常发起请求（Integration 测试用 mock WebServer）
- [ ] 配置 OpenRouter（`base-url: https://openrouter.ai/api`，免费模型 `meta-llama/llama-3.3-70b-instruct:free`）能正常完成一轮含工具调用的对话
- [ ] `ConversationService` 行数 < 100 行（已拆分至各子服务）

---

## 2. 第二期：工具生态 + MCP 协议

**目标**：把内置工具从 2 个扩展到实用水平，并通过 MCP 协议接入无限第三方工具服务器。

### 2.1 并行工具执行完善

第一期中 `ChatOrchestrator` 已支持并行，本期重点是确保 `ToolService` 能接收 `List<ToolCallRequest>` 并用 `Flux.merge` 并发执行，所有工具结果都有正确的审计日志（每个工具独立一条 `TOOL_AUDIT` 日志）。

原有 `ToolService.execute(String, String, String, String)` 保留（单工具），新增：
```java
Flux<ToolExecutionResult> executeAll(String conversationId, List<ToolCallRequest> requests);
```
内部用 `Flux.fromIterable(requests).flatMap(req → execute(...))` 实现并发。

---

### 2.2 新增内置工具

所有工具都实现 `Tool` 接口，加 `@NsbhTool` 注解，自动注册到 `ToolRegistry`，无需修改任何注册代码。

#### 2.2.1 web_search 工具

搜索结果是 agent 获取实时信息的主要手段。

**配置**：
```yaml
nsbh:
  tools:
    web-search:
      provider: tavily      # tavily | serper | bing
      api-key: ${TAVILY_API_KEY:}
      max-results: 5
```

**实现要点**：
- `TavilySearchTool`、`SerperSearchTool` 分别实现 `Tool`，用 `WebClient` 全 reactive
- 入参：`{"query": "...", "max_results": 5}`
- 出参：结构化 JSON，包含 title、url、snippet 列表
- 权限：`requiredPermissions = {"NET_HTTP"}`
- SSRF 防护复用 `HttpGetTool` 的 `validateResolvedAddresses`

#### 2.2.2 read_file 工具

让 agent 能读取用户 workspace 内的文件。

**安全沙箱**：所有文件操作必须限制在 `nsbh.workspace.root` 目录内，拒绝任何包含 `..` 的路径。

```yaml
nsbh:
  workspace:
    root: ${user.home}/.nsbh/workspace
```

**实现**：
```java
@NsbhTool(
    name = "read_file",
    description = "Read a file from the workspace",
    schema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}",
    requiredPermissions = {"WORKSPACE_READ"}
)
public class ReadFileTool implements Tool {
    // 1. 解析 path
    // 2. resolve 到 workspace root 下的绝对路径
    // 3. 检查 resolvedPath.startsWith(workspaceRoot)，否则拒绝
    // 4. Mono.fromCallable(() → Files.readString(resolvedPath)).subscribeOn(Schedulers.boundedElastic())
}
```

#### 2.2.3 write_file 工具

入参：`{"path": "...", "content": "..."}`，自动创建父目录，同样沙箱限制。

```java
@NsbhTool(
    name = "write_file",
    requiredPermissions = {"WORKSPACE_WRITE"}
)
```

#### 2.2.4 list_files 工具

列出 workspace 内某目录的文件树。入参：`{"path": ".", "recursive": false}`。

#### 2.2.5 shell 工具

**这是高危工具，默认权限不开启，必须用户显式配置才能使用。**

入参：`{"command": "ls -la /workspace"}`

安全策略：
- 命令前缀 allowlist：`nsbh.tools.shell.allowed-prefixes: ["ls", "cat", "grep", "python3", "node"]`
- 工作目录固定为 workspace root
- 超时强制 kill（`ProcessBuilder` + `process.destroyForcibly()`）
- 禁止 `sudo`、`rm -rf /`、`chmod 777 /` 等高危命令（关键词黑名单）
- stdout + stderr 合并返回，限制 `maxOutputBytes`
- 权限：`requiredPermissions = {"SHELL_EXEC"}`

```java
@NsbhTool(
    name = "shell",
    description = "Execute a shell command in the workspace (requires explicit permission)",
    schema = "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"}},\"required\":[\"command\"]}",
    requiredPermissions = {"SHELL_EXEC"}
)
public class ShellTool implements Tool {
    // ProcessBuilder，subscribeOn(Schedulers.boundedElastic())
}
```

#### 2.2.6 think 工具

灵感来自 Anthropic 的 "thinking" 功能，让 LLM 能显式"暂停推理"。

入参：`{"thought": "..."}`，不调任何外部服务，直接返回 `{"thought": ..., "status": "ok"}`。

作用：LLM 可以先调 `think` 把推理过程写出来，再决定下一步调哪个真实工具，提升复杂任务的准确率。无需任何权限。

---

### 2.3 MCP（Model Context Protocol）Client

MCP 是 Anthropic 主导的工具标准协议，接入后可连接数百个社区 MCP 服务器（文件系统、数据库、GitHub、浏览器控制等）。

#### 2.3.1 MCP 协议简介

MCP 使用 JSON-RPC 2.0 协议，支持两种传输：
- **stdio**：启动子进程，通过 stdin/stdout 通信（适合本地工具）
- **SSE**：HTTP + Server-Sent Events（适合远程服务）

核心 RPC 方法：
- `initialize`：握手，获取服务器能力
- `tools/list`：获取工具列表（name、description、inputSchema）
- `tools/call`：调用工具

#### 2.3.2 实现架构

```
mcp/
├── McpClient.java              ← MCP 通信接口
├── McpStdioClient.java         ← stdio 传输实现
├── McpSseClient.java           ← SSE 传输实现
├── McpToolAdapter.java         ← 把 MCP tool 包装为 Tool 接口
└── McpServerRegistry.java      ← 启动时连接所有配置的 MCP server
```

**`McpClient` 接口**：

```java
public interface McpClient {
    Mono<List<McpToolDefinition>> listTools();
    Mono<String> callTool(String name, String inputJson);
    Mono<Void> close();
}
```

**`McpStdioClient`** 实现：
- 用 `ProcessBuilder` 启动子进程
- 通过 `process.getInputStream()` / `process.getOutputStream()` 做 JSON-RPC
- 所有 I/O 在 `Schedulers.boundedElastic()` 上执行
- 维护请求 ID → 响应 `Mono` 的映射（`Map<String, MonoSink<JsonNode>>`）

**`McpToolAdapter`**：

```java
public class McpToolAdapter implements Tool {
    private final McpClient mcpClient;
    private final McpToolDefinition definition;

    @Override
    public Mono<String> execute(String inputJson) {
        return mcpClient.callTool(definition.name(), inputJson);
    }
}
```

**`McpServerRegistry`**：在 `ApplicationRunner` 中，对每个配置的 MCP server：
1. 创建对应 `McpClient`（stdio 或 SSE）
2. 调 `listTools()` 获取工具列表
3. 为每个工具创建 `McpToolAdapter` 并注册到 `ToolRegistry`
4. 关闭时（`@PreDestroy`）调 `close()` 清理子进程

**配置**：

```yaml
nsbh:
  mcp:
    servers:
      - name: filesystem
        transport: stdio
        command: ["npx", "-y", "@modelcontextprotocol/server-filesystem", "/home/user/workspace"]
      - name: github
        transport: stdio
        command: ["npx", "-y", "@modelcontextprotocol/server-github"]
        env:
          GITHUB_PERSONAL_ACCESS_TOKEN: ${GITHUB_TOKEN:}
      - name: remote-server
        transport: sse
        url: "http://localhost:3001/sse"
        headers:
          Authorization: "Bearer ${MCP_TOKEN:}"
```

`NsbhProperties` 增加 `Mcp` 内部类，持有 `List<McpServerConfig>`。

---

### 2.4 第二期验收标准

- [ ] `mvn test` 全绿
- [ ] `web_search` 工具对接 Tavily API，返回结构化搜索结果
- [ ] `read_file` / `write_file` 路径穿越攻击（`../../../etc/passwd`）被拒绝
- [ ] `shell` 工具未配置 `SHELL_EXEC` 权限时被拒绝，配置后能执行 allowlist 内命令
- [ ] 配置一个 stdio MCP server（如 `@modelcontextprotocol/server-filesystem`），其工具出现在 `/api/v1/tools` 列表
- [ ] MCP server 断开时，对应工具从 `ToolRegistry` 移除（或返回明确错误）
- [ ] 并行工具调用：使用 OpenRouter 免费模型（`google/gemini-2.0-flash-exp:free`）触发同时调用 `time` + `web_search`，两个工具并发执行，审计日志中两条记录时间戳相近

---

## 3. 第三期：多渠道接入

**目标**：从纯 REST API 扩展到主流 IM 平台，让 agent 真正"活"在用户的聊天 app 里。

### 3.1 渠道抽象层

在接具体 IM 之前，先建立统一的渠道抽象，避免每个渠道都硬编码业务逻辑。

**目录结构**：

```
channel/
├── Channel.java                ← 渠道接口
├── InboundMessage.java         ← 统一入站消息模型
├── OutboundMessage.java        ← 统一出站消息模型
├── ChannelRouter.java          ← 路由入站消息到 ChatOrchestrator
├── SessionMapper.java          ← 渠道用户 ID ↔ conversationId 映射（DB 持久化）
├── telegram/
│   └── TelegramChannel.java
├── discord/
│   └── DiscordChannel.java
├── slack/
│   └── SlackChannel.java
└── openai/
    └── OpenAiCompatibleController.java
```

**`Channel` 接口**：

```java
public interface Channel {
    String channelId();                           // 渠道唯一标识，如 "telegram"
    Flux<InboundMessage> inbound();               // 接收消息的响应式流
    Mono<Void> send(OutboundMessage message);     // 发送回复
    Mono<Void> sendStream(String sessionId, Flux<String> chunks);  // 流式发送（如有）
}
```

**`InboundMessage`**：

```java
public record InboundMessage(
    String channelId,       // 来自哪个渠道
    String sessionId,       // 渠道内的会话标识（如 telegram chat_id）
    String userId,          // 渠道内的用户标识
    String text,            // 文本内容
    List<Attachment> attachments,  // 图片、文件等
    Instant timestamp
) {}
```

**`SessionMapper`**：DB 新增 `channel_sessions` 表，字段：`channel_id`、`session_id`、`conversation_id`（UUID）、`created_at`。

每次收到消息时，先查 `channel_sessions`，没有则创建新 conversation 并写入映射。

**`ChannelRouter`**：

```java
@Service
public class ChannelRouter {
    // 订阅所有 Channel.inbound() 合并流
    // 对每条 InboundMessage：
    //   1. 查 SessionMapper 得到 conversationId（没有则创建）
    //   2. 调 ChatOrchestrator.orchestrate()
    //   3. 把结果通过 channel.send() 发回
}
```

---

### 3.2 OpenAI 兼容 API（最重要的渠道）

接入 OpenAI 兼容 API 后，所有支持 OpenAI 的客户端（Cherry Studio、Open WebUI、Continue.dev、Cursor 等）可以直接对接 NSBH。

**新增端点**：

```
POST /v1/chat/completions     ← 兼容 OpenAI Chat Completions API
GET  /v1/models               ← 返回可用模型列表
```

**请求格式**（标准 OpenAI）：

```json
{
  "model": "gpt-4.1-mini",
  "messages": [{"role": "user", "content": "你好"}],
  "stream": true,
  "user": "user-123"
}
```

**会话映射**：用 `user` 字段（或 `Authorization` Bearer token）作为 `session_id`，映射到 `conversationId`。

**响应格式**：
- `stream: false`：完整的 `ChatCompletionObject` JSON
- `stream: true`：SSE 格式，每个 delta 一条 `data: {...}` 行，结束时 `data: [DONE]`

**实现**：

```java
@RestController
@RequestMapping("/v1")
public class OpenAiCompatibleController {

    @PostMapping(value = "/chat/completions", produces = {
        MediaType.APPLICATION_JSON_VALUE,
        MediaType.TEXT_EVENT_STREAM_VALUE
    })
    public Mono<ResponseEntity<?>> chatCompletions(@RequestBody OpenAiChatRequest req) {
        if (Boolean.TRUE.equals(req.stream())) {
            return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(buildStreamResponse(req)));
        }
        return buildBatchResponse(req).map(ResponseEntity::ok);
    }
}
```

---

### 3.3 Telegram 渠道

**依赖**：

```xml
<dependency>
    <groupId>org.telegram</groupId>
    <artifactId>telegrambots-spring-boot-starter</artifactId>
    <version>6.9.7.1</version>
</dependency>
```

**`TelegramChannel`** 实现：

- 继承 `TelegramLongPollingBot`（长轮询）或配置 Webhook
- `onUpdateReceived()` 把 `Update` 转为 `InboundMessage`，push 到内部 `Sinks.Many`
- `send()` 调 `execute(new SendMessage(chatId, text))`
- 流式发送：用 `EditMessageText` 实时更新同一条消息（nanobot 同款）

**配置**：

```yaml
nsbh:
  channels:
    telegram:
      enabled: false
      bot-token: ${TELEGRAM_BOT_TOKEN:}
      bot-username: ${TELEGRAM_BOT_USERNAME:}
      mode: polling    # polling | webhook
      webhook-url: ""  # mode=webhook 时必填
```

**多模态支持**：
- 用户发送图片：下载为 byte[]，转为 base64，加入 `InboundMessage.attachments`
  - 若 LLM provider 支持视觉（如 GPT-4V、Claude），传入 message content 中的 image_url 部分
- 用户发送文件：下载到 workspace，告知 LLM 文件路径，让它用 `read_file` 工具读取

---

### 3.4 Discord 渠道

**依赖**（原生 reactive）：

```xml
<dependency>
    <groupId>com.discord4j</groupId>
    <artifactId>discord4j-core</artifactId>
    <version>3.2.6</version>
</dependency>
```

**`DiscordChannel`** 实现：

- `GatewayDiscordClient` 监听 `MessageCreateEvent`
- 过滤条件：消息包含 `@mention` 机器人，或私信（DM）
- 每个 `channel_id`（或 `dm_channel_id`）对应一个 session
- 长消息自动分割（Discord 单条 2000 字符限制）
- 在频道内创建 Thread 隔离对话（可配置）

**配置**：

```yaml
nsbh:
  channels:
    discord:
      enabled: false
      bot-token: ${DISCORD_BOT_TOKEN:}
      allowed-guild-ids: []   # 空列表 = 不限制
      use-threads: true
```

---

### 3.5 Slack 渠道

**依赖**：

```xml
<dependency>
    <groupId>com.slack.api</groupId>
    <artifactId>bolt</artifactId>
    <version>1.40.3</version>
</dependency>
<dependency>
    <groupId>com.slack.api</groupId>
    <artifactId>bolt-servlet</artifactId>
    <version>1.40.3</version>
</dependency>
```

**`SlackChannel`** 实现：

- Slack Bolt for Java，事件驱动
- 监听 `app_mention` 事件和 DM 消息
- 用 `client.chatPostMessage()` 回复
- 流式更新：先发一条占位消息，然后用 `client.chatUpdate()` 持续更新

**配置**：

```yaml
nsbh:
  channels:
    slack:
      enabled: false
      bot-token: ${SLACK_BOT_TOKEN:}
      signing-secret: ${SLACK_SIGNING_SECRET:}
      app-token: ${SLACK_APP_TOKEN:}    # Socket Mode 用
      mode: socket     # socket | http
```

---

### 3.6 第三期验收标准

- [ ] `mvn test` 全绿
- [ ] `curl -X POST http://localhost:8080/v1/chat/completions -d '{"model":"mock","messages":[{"role":"user","content":"hi"}],"stream":false}'` 返回 OpenAI 兼容格式
- [ ] 同上，`"stream":true` 返回正确 SSE 流
- [ ] Cherry Studio / Open WebUI 配置 NSBH 地址，后端使用 OpenRouter 免费模型，能正常对话
- [ ] Telegram bot 能正常聊天，后端使用 OpenRouter 免费模型，工具调用结果正确返回
- [ ] Discord bot 在 @mention 时响应，后端使用 OpenRouter 免费模型，长回复自动分割
- [ ] Slack bot 在 app_mention 时响应，后端使用 OpenRouter 免费模型

---

## 4. 第四期：记忆升级 + 技能系统

**目标**：解决长会话记忆失真问题，建立技能插件生态，增加自然语言调度能力。

### 4.1 Token 计数记忆窗口

**现状问题**：用消息条数（`memory.window = 20`）控制 prompt 大小不准确。一条消息可能是 3 token（"好"），也可能是 3000 token（一段代码）。实际发送给 LLM 的 prompt 大小完全不可控，容易超出模型 context limit。

**改造方案**：

引入 `jtokkit`（Java 版 tiktoken）做 token 计数：

```xml
<dependency>
    <groupId>com.knuddels</groupId>
    <artifactId>jtokkit</artifactId>
    <version>1.1.0</version>
</dependency>
```

`PromptBuilder.buildPromptWindow()` 改为 token 预算控制：

```java
private List<MessageEntity> selectByTokenBudget(List<MessageEntity> normals, int maxTokens) {
    EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
    Encoding enc = registry.getEncoding(EncodingType.CL100K_BASE);

    int used = 0;
    Deque<MessageEntity> selected = new ArrayDeque<>();
    // 从最新消息往前堆，超出预算则停止
    for (int i = normals.size() - 1; i >= 0; i--) {
        int tokens = enc.countTokens(normals.get(i).getContent());
        if (used + tokens > maxTokens) break;
        selected.addFirst(normals.get(i));
        used += tokens;
    }
    return new ArrayList<>(selected);
}
```

**新配置**（`window` 和 `compactAfter` 改为 token 单位）：

```yaml
nsbh:
  memory:
    max-prompt-tokens: 8000      # prompt window 最大 token 数
    compact-after-tokens: 16000  # 超过此 token 数触发 compaction
    system-prompt: "..."
```

保留 `window`（消息条数）作为备用上限，两个条件任一触发则截断。

---

### 4.2 两阶段记忆（对标 nanobot Dream）

nanobot 的 Dream 机制：短期对话 → 定期整合为长期记忆 → 新会话时按语义检索相关记忆注入。

**NSBH 实现方案**：

**阶段一（Working Memory）**：当前会话滑动窗口，已有，本期强化为 token 计数版本。

**阶段二（Episodic Memory）**：

新增数据库表 `episodic_memories`：

```sql
CREATE TABLE episodic_memories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,       -- 跨会话的用户标识
    conversation_id UUID,                -- 来源会话
    content TEXT NOT NULL,               -- 压缩后的记忆内容
    keywords TEXT,                       -- 关键词（逗号分隔，用于检索）
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

**`EpisodicMemoryService`**：

```java
@Service
public class EpisodicMemoryService {

    // 会话结束或超阈值时，把当前 NORMAL 消息压缩存入 episodic_memories
    public Mono<Void> consolidate(String userId, UUID conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
            .collectList()
            .flatMap(messages -> llmClient.summarize(messages, model))
            .flatMap(summary -> {
                EpisodicMemory mem = new EpisodicMemory();
                mem.setUserId(userId);
                mem.setConversationId(conversationId);
                mem.setContent(summary);
                mem.setKeywords(extractKeywords(summary));
                return episodicMemoryRepository.save(mem);
            }).then();
    }

    // 新会话时，根据用户 ID 和当前消息检索相关历史记忆
    public Flux<EpisodicMemory> retrieve(String userId, String query) {
        // 初期：关键词匹配，从 query 提取词，与 keywords 列做 LIKE 查询
        // 后期：接向量数据库（pgvector）做语义检索
        return episodicMemoryRepository.findRelevantByUserIdAndKeywords(userId, extractKeywords(query));
    }
}
```

**注入时机**：`PromptBuilder.buildPromptWindow()` 在 system prompt 之后、SUMMARY 之前，插入检索到的历史记忆（格式：`[Long-term memory from previous conversations]\n...`）。

**初期关键词检索**，后期可替换为 pgvector 语义检索，接口不变。

---

### 4.3 技能系统（Skills）

对标 nanobot Skills，让用户定义预设的 agent 行为模板，切换技能即切换 agent 的"角色"和可用工具集。

**技能 YAML 格式**（`~/.nsbh/skills/coding-assistant.yaml`）：

```yaml
id: coding-assistant
name: 代码助手
description: 专注于代码生成、审查和调试的技能
system_prompt_addon: |
  你是一个资深软件工程师。回答时优先考虑代码质量、安全性和可维护性。
  遇到不确定的问题时，先用 think 工具分析，再给出方案。
allowed_tools:
  - read_file
  - write_file
  - shell
  - web_search
required_permissions:
  - WORKSPACE_READ
  - WORKSPACE_WRITE
  - SHELL_EXEC
trigger_keywords:
  - 写代码
  - 帮我实现
  - 代码审查
  - debug
```

**`SkillDefinition`** record：持有 YAML 解析后的所有字段。

**`SkillRegistry`**：启动时扫描 `~/.nsbh/skills/` 和 classpath `/skills/` 目录，加载所有 YAML 文件，存入 `Map<String, SkillDefinition>`。内置几个默认技能：`general`（通用）、`coding`（代码）、`research`（研究）。

**`SkillSelector`**：

```java
@Service
public class SkillSelector {
    // 根据用户消息匹配最合适的技能
    public Optional<SkillDefinition> select(String userMessage) {
        return skillRegistry.all().stream()
            .filter(skill -> skill.triggerKeywords().stream()
                .anyMatch(kw -> userMessage.toLowerCase().contains(kw.toLowerCase())))
            .findFirst();
    }
}
```

**技能激活**：在 `ChatOrchestrator` 的每次对话前调用 `SkillSelector.select()`，如果匹配到技能：
1. `PromptBuilder` 在 system prompt 末尾追加 `system_prompt_addon`
2. `ToolService` 的 allowlist 改为技能指定的 `allowed_tools`（与全局 allowlist 取交集）

用户也可以在对话中显式切换：`/skill coding-assistant`（通过 in-chat 命令路由）。

---

### 4.4 对话内命令系统

支持类似 nanobot 的 `/command` 语法，在聊天时执行管理操作。

**实现**：`ChannelRouter` 在路由前检查消息是否以 `/` 开头：

```java
if (message.text().startsWith("/")) {
    return commandHandler.handle(message);
}
return chatOrchestrator.orchestrate(...);
```

**内置命令**：

| 命令 | 说明 |
|------|------|
| `/skill <id>` | 切换技能 |
| `/history [n]` | 显示最近 n 条对话记录 |
| `/clear` | 清空当前会话记忆 |
| `/status` | 显示当前配置（provider、model、技能、工具） |
| `/jobs` | 显示当前所有定时任务 |
| `/help` | 显示命令列表 |

---

### 4.5 自然语言调度

**现状问题**：`DailySummaryScheduler` 只有固定 cron，不能动态添加任务。

**新增 `SchedulerService`**：

DB 新增 `scheduled_jobs` 表：

```sql
CREATE TABLE scheduled_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    description TEXT,
    task_prompt TEXT NOT NULL,    -- 到时间后发给 agent 的消息
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

**`schedule` 工具**（agent 可调用）：

```java
@NsbhTool(
    name = "schedule",
    description = "Schedule a recurring task with a cron expression or natural language time description",
    schema = "{\"type\":\"object\",\"properties\":{\"description\":{\"type\":\"string\"},\"cron\":{\"type\":\"string\"},\"task\":{\"type\":\"string\"}},\"required\":[\"description\",\"cron\",\"task\"]}"
)
public class ScheduleTool implements Tool {
    // 把 cron + task 写入 scheduled_jobs 表
    // 注册到 SchedulerService（动态注册 Spring @Scheduled 任务）
}
```

**自然语言 → cron 转换**：用 LLM 解析，在 `ChatOrchestrator` 里有一个专门的 `parseCron(String naturalLanguage)` 步骤，或者 agent 自己调 `think` 工具推理出 cron 表达式再调 `schedule` 工具。

---

### 4.6 第四期验收标准

- [ ] `mvn test` 全绿
- [ ] 发送 3000 token 的消息，prompt window 不超过 `max-prompt-tokens` 配置
- [ ] 两次不同会话（使用 OpenRouter 免费模型），第二次能检索到第一次的历史记忆并注入 prompt，LLM 回复中体现出对历史的感知
- [ ] 加载自定义 YAML 技能，触发关键词时 system prompt 正确追加
- [ ] `/skill coding` 命令切换技能
- [ ] 对话内输入"每天早上 9 点提醒我喝水"（使用 OpenRouter 免费模型解析），定时任务被创建，`/jobs` 命令能看到正确的 cron 表达式

---

## 5. 第五期：WebUI + 生产化

**目标**：完整可部署的产品，对标 nanobot 的 WebUI、Docker、可观测性。

### 5.1 WebUI

**技术选型**：React 18 + TypeScript + TailwindCSS，bun 构建。WebSocket 通信（Spring WebFlux 原生支持）。

**目录结构**：

```
webui/
├── src/
│   ├── components/
│   │   ├── ChatWindow.tsx       ← 消息列表 + 输入框
│   │   ├── MessageBubble.tsx    ← 单条消息，支持 Markdown 渲染
│   │   ├── ToolCallCard.tsx     ← 折叠展示工具调用详情
│   │   ├── ConversationList.tsx ← 左侧会话列表
│   │   └── SettingsPanel.tsx    ← 技能切换、模型选择
│   ├── hooks/
│   │   └── useWebSocket.ts      ← WebSocket 连接管理，自动重连
│   └── App.tsx
├── package.json
└── bun.lockb
```

**WebSocket 协议**（自定义，运行在 `/ws/chat`）：

```
客户端 → 服务器：
{"type":"chat","conversationId":"...","message":"...","model":"..."}

服务器 → 客户端（流式）：
{"type":"chunk","data":"..."}           ← LLM 文本增量
{"type":"tool_start","toolName":"..."}  ← 工具开始执行
{"type":"tool_end","toolName":"...","result":"..."} ← 工具执行完成
{"type":"done"}                         ← 本轮结束
{"type":"error","message":"..."}        ← 错误
```

**Maven 集成**：用 `frontend-maven-plugin` 在 `mvn package` 时自动执行 `bun install && bun run build`，输出目录映射到 `src/main/resources/static/`，打入 jar 包内。

**`WebSocketChatHandler`**（Spring WebFlux）：

```java
@Component
public class WebSocketChatHandler implements WebSocketHandler {
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .flatMap(payload -> handleMessage(session, payload))
            .then();
    }
}
```

---

### 5.2 CLI 模式

引入 `spring-shell`，让 NSBH 也能像 nanobot 一样在终端使用：

```xml
<dependency>
    <groupId>org.springframework.shell</groupId>
    <artifactId>spring-shell-starter</artifactId>
    <version>3.3.3</version>
</dependency>
```

**命令**：

```bash
nsbh onboard    # 交互式向导：配置 provider、API key、渠道
nsbh agent      # 启动完整 agent 服务（API + 渠道 + WebUI）
nsbh chat       # 终端直接聊天（不启动 HTTP 服务）
nsbh skills     # 列出所有技能
nsbh tools      # 列出所有可用工具
```

`nsbh chat` 实现：用 `LineReader`（JLine3）读取终端输入，调 `ChatOrchestrator`，流式输出到终端（模拟打字效果）。

---

### 5.3 Docker + 部署

**`Dockerfile`**（多阶段构建）：

```dockerfile
# Stage 1: 构建前端
FROM oven/bun:1 AS frontend-builder
WORKDIR /webui
COPY webui/package.json webui/bun.lockb ./
RUN bun install --frozen-lockfile
COPY webui/ .
RUN bun run build

# Stage 2: 构建后端
FROM maven:3.9-eclipse-temurin-21 AS backend-builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src/ src/
COPY --from=frontend-builder /webui/dist src/main/resources/static/
RUN mvn package -DskipTests -q

# Stage 3: 运行时
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S nsbh && adduser -S nsbh -G nsbh
USER nsbh
COPY --from=backend-builder /app/target/nsbh-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**`docker-compose.yml`**：

```yaml
version: "3.9"
services:
  nsbh:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - NSBH_LLM_PROVIDER=openai
      - NSBH_LLM_API_KEY=${OPENAI_API_KEY}
      - SPRING_R2DBC_URL=r2dbc:postgresql://postgres:5432/nsbh
      - SPRING_R2DBC_USERNAME=nsbh
      - SPRING_R2DBC_PASSWORD=${DB_PASSWORD}
    depends_on:
      postgres:
        condition: service_healthy
    volumes:
      - nsbh-workspace:/home/nsbh/.nsbh/workspace

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: nsbh
      POSTGRES_USER: nsbh
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U nsbh"]
      interval: 5s
      timeout: 5s
      retries: 5

  langfuse:          # 可选，可观测性
    image: langfuse/langfuse:latest
    ports:
      - "3000:3000"
    environment:
      - DATABASE_URL=postgresql://nsbh:${DB_PASSWORD}@postgres:5432/langfuse

volumes:
  postgres-data:
  nsbh-workspace:
```

**Linux systemd service**（`/etc/systemd/system/nsbh.service`）：

```ini
[Unit]
Description=NSBH AI Agent
After=network.target

[Service]
Type=simple
User=nsbh
WorkingDirectory=/opt/nsbh
ExecStart=/usr/bin/java -jar /opt/nsbh/nsbh.jar
EnvironmentFile=/opt/nsbh/.env
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

---

### 5.4 可观测性

**Micrometer + Prometheus**：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**自定义指标**（在 `ChatOrchestrator`、`ToolService`、`OpenAiLlmClient` 中埋点）：

| 指标名 | 类型 | 标签 |
|--------|------|------|
| `nsbh.llm.requests.total` | Counter | `provider`, `model`, `status` |
| `nsbh.llm.request.duration` | Timer | `provider`, `model` |
| `nsbh.llm.tokens.used` | Counter | `provider`, `model`, `type`(prompt/completion) |
| `nsbh.tool.executions.total` | Counter | `tool_name`, `status` |
| `nsbh.tool.execution.duration` | Timer | `tool_name` |
| `nsbh.agent.rounds.per.chat` | Histogram | - |
| `nsbh.conversations.active` | Gauge | - |

**Langfuse 集成**（LLM 调用可观测性平台）：

```xml
<dependency>
    <groupId>com.langfuse</groupId>
    <artifactId>langfuse-java</artifactId>
    <version>1.x.x</version>
</dependency>
```

在 `ChatOrchestrator` 中：
- 每次 `orchestrate()` 开始时创建 Langfuse `Trace`
- 每次 `llmClient.firstReply()` 创建子 `Generation` span，记录 prompt、completion、token 用量
- 每次 `toolService.execute()` 创建子 `Span`，记录工具名、输入输出、耗时
- `orchestrate()` 结束时关闭 Trace，标记 success/error

**结构化日志扩展**：现有 `JsonLogFormatter` 增加全局字段：

```json
{
  "timestamp": "...",
  "level": "INFO",
  "requestId": "...",
  "traceId": "...",
  "provider": "openai",
  "model": "gpt-4.1-mini",
  "conversationId": "...",
  "message": "..."
}
```

---

### 5.5 多实例支持

当多个 NSBH 实例横向扩展时，需要解决：

1. **WebSocket 会话亲和**：用 Nginx `ip_hash` 或 sticky session
2. **定时任务去重**：引入 `ShedLock`（基于 DB 的分布式锁）确保 cron 任务只在一个实例执行
3. **渠道接入去重**：Telegram webhook 天然只有一个接收端；长轮询模式需要用 DB 锁保证只有一个实例拉取

---

### 5.6 第五期验收标准

- [ ] `mvn test` 全绿
- [ ] `docker compose up` 30 秒内完整启动
- [ ] 浏览器访问 `http://localhost:8080` 打开 WebUI，能正常多会话聊天
- [ ] Prometheus 抓取 `http://localhost:8080/actuator/prometheus`，能看到 `nsbh.*` 指标
- [ ] Langfuse dashboard 能看到完整 agent trace，含工具调用 span
- [ ] `nsbh chat` 命令在终端能正常对话
- [ ] 镜像大小 < 300MB

---

## 6. 里程碑总览

```
第一期  ████████████████████  Agent Loop + Stream + Multi-Provider
          核心地基，最重要，其他一切依赖它

第二期  ██████████████████    工具生态 + MCP
          Agent 的"手脚"，实用性关键

第三期  ████████████████      多渠道接入
          用户触点，产品化必须

第四期  ██████████████        记忆升级 + 技能系统
          差异化能力，中期目标

第五期  ████████████          WebUI + 生产化
          成熟度标志，最后打磨
```

### 每期关键依赖项变更

| 期 | 新增依赖 |
|----|---------|
| 一期 | 无新依赖（重构现有代码） |
| 二期 | `jtokkit`（可选，token 计数） |
| 三期 | `telegrambots-spring-boot-starter`、`discord4j-core`、`bolt-servlet` |
| 四期 | `jtokkit`（正式引入）、`snakeyaml`（技能 YAML）、`spring-shell` |
| 五期 | `micrometer-registry-prometheus`、`langfuse-java`、`frontend-maven-plugin` |

### 不变的约束

以下约束在整个演进过程中保持不变，不允许降级：

1. 端到端 non-blocking，禁止在 Reactor 链路主路径调 `.block()`
2. SSRF 防护不可回退（`HttpGetTool.validateResolvedAddresses`）
3. 工具执行必须经过 allowlist + permission 双重检查
4. 所有 I/O 必须是 reactive 或显式隔离到 `boundedElastic`
5. 每期结束 `mvn test` 必须全绿，不允许带着失败的测试进入下一期

---

## 7. 当前代码债务（开始前需注意）

在启动第一期之前，以下已知问题需要同步处理，否则会影响后续工作：

| 问题 | 位置 | 处理方式 |
|------|------|----------|
| `OpenAiLlmClient.firstReply()` 只取第一个工具调用 | `OpenAiLlmClient.java:62` | 第一期 1.4 节改造 |
| `ConversationService` 职责过重 240+ 行 | `ConversationService.java` | 第一期 1.2 节拆分 |
| `HttpGetTool` 使用阻塞 HTTP client | `HttpGetTool.java:49` | 第二期改为 `WebClient` |
| `buildPromptWindow` 每次全量拉取所有消息 | `ConversationService.java:186` | 第四期 token 窗口改造时优化 |
| `maybeCompactMemory` 每次 chat 都扫全量消息 | `ConversationService.java:153` | 同上 |
| E:\maven_repository 目录被误加入工作区 | 项目根目录 | 加入 `.gitignore` |
