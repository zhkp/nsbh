# Phase 3: OpenAI Compatible API — Design Spec

> **For agentic workers:** Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Expose an OpenAI-compatible `/v1/chat/completions` endpoint so that standard clients (Cherry Studio, Open WebUI, Continue.dev, Cursor, etc.) can use NSBH as a drop-in OpenAI backend.

**Scope:** API key authentication + stateless OpenAI-compatible API. Channel abstraction and IM channels (Telegram, Discord, Slack) are explicitly deferred.

---

## Architecture

Three new components, no new abstractions:

```
api/
└── ApiKeyAuthFilter.java         — Bearer token 校验，拦截 /v1/**

openai/
├── OpenAiCompatController.java   — GET /v1/models + POST /v1/chat/completions
├── OpenAiChatRequest.java        — 入参 record
├── OpenAiChatResponse.java       — 非流式出参 record
└── OpenAiChatChunk.java          — 流式 delta record

agent/
└── StatelessOrchestrator.java    — 多轮 agent loop，以传入 messages[] 为上下文
```

`NsbhProperties` 新增 `api.key` 字段。

No channel abstraction layer — YAGNI until IM channels are implemented.

---

## Section 1: Authentication

`ApiKeyAuthFilter` implements Spring WebFlux `WebFilter`, intercepts only `/v1/**`.

**Logic:**
1. Extract `Authorization` header; expect `Bearer <key>`
2. Compare against `nsbh.api.key`
3. Mismatch → `401 Unauthorized` with body `{"error":"invalid_api_key"}`
4. If `nsbh.api.key` is blank, skip validation (local dev convenience)

**Config:**
```yaml
nsbh:
  api:
    key: ${NSBH_API_KEY:}   # blank = no auth
```

`NsbhProperties` gains an `Api` inner class with a single `key` field.

---

## Section 2: StatelessOrchestrator

Takes the client-provided `messages[]`, runs a multi-round agent loop entirely in memory, no DB reads or writes.

**Signature:**
```java
public Mono<String> chat(List<OpenAiMessage> messages, String model);
public Flux<String> stream(List<OpenAiMessage> messages, String model);
```

**LlmClient extension:** Add overload `LlmReply firstReply(List<MessageEntity> messages, String model)` and `Flux<String> streamFirstReply(List<MessageEntity> messages, String model)` to the `LlmClient` interface. Each provider implements by using the full list as the message array directly (no userMessage separation). Existing overloads stay unchanged.

**Loop logic:**
1. Convert `List<OpenAiMessage>` to `List<MessageEntity>` (role mapping: user→USER, assistant→ASSISTANT, system→SYSTEM, tool→TOOL)
2. Call `LlmClient.firstReply(converted, model)`
3. If no tool calls → return assistant message, done
4. If tool calls → `ToolService.executeAll()` (parallel), append tool results to messages, `round++`
5. If `round >= nsbh.agent.max-tool-rounds` → stop and return whatever the last assistant message was

**Stream variant:** `LlmClient.streamFirstReply()` tokens are passed through directly. Tool-call rounds are non-streaming (wait for tools, then continue streaming the next LLM response).

**Dependencies:** `LlmClient`, `ToolService`, `NsbhProperties`. No repository dependencies.

---

## Section 3: OpenAI Compatible API

### GET /v1/models

Returns the configured model as a single-item list:
```json
{
  "object": "list",
  "data": [{"id": "gpt-4.1-mini", "object": "model", "owned_by": "nsbh"}]
}
```
Model id comes from `nsbh.llm.model-default`.

### POST /v1/chat/completions

**Request** (standard OpenAI; only used fields parsed):
```json
{
  "model": "gpt-4.1-mini",
  "messages": [{"role": "user", "content": "hello"}],
  "stream": false,
  "temperature": 0.7
}
```

**Non-stream response** (`Content-Type: application/json`):
```json
{
  "id": "chatcmpl-<uuid>",
  "object": "chat.completion",
  "created": 1234567890,
  "model": "gpt-4.1-mini",
  "choices": [{
    "index": 0,
    "message": {"role": "assistant", "content": "..."},
    "finish_reason": "stop"
  }],
  "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}
}
```
`usage` fields are 0 — token counting is a Phase 4 concern.

**Stream response** (`Content-Type: text/event-stream`):
```
data: {"id":"chatcmpl-<uuid>","object":"chat.completion.chunk","created":1234567890,"model":"...","choices":[{"index":0,"delta":{"role":"assistant"},"finish_reason":null}]}

data: {"id":"chatcmpl-<uuid>","object":"chat.completion.chunk","created":1234567890,"model":"...","choices":[{"index":0,"delta":{"content":"hello"},"finish_reason":null}]}

data: [DONE]

```

Controller delegates to `StatelessOrchestrator.chat()` or `.stream()` depending on `stream` flag.

---

## Section 4: Error Handling

All errors use OpenAI-compatible error format to prevent client parse failures:
```json
{"error": {"message": "...", "type": "invalid_request_error", "code": null}}
```

| Condition | HTTP Status |
|-----------|-------------|
| API key mismatch | 401 |
| messages null/empty | 400 |
| LLM call fails | 502 |
| Tool execution timeout | 504 |

`OpenAiCompatController` handles its own errors via `Mono.onErrorResume`, formatting them as OpenAI error JSON before returning. The existing `GlobalExceptionHandler` is not modified — it only covers non-`/v1/**` paths.

---

## Section 5: Testing

**`ApiKeyAuthFilterTest`** (unit):
- Valid key → request passes through
- Invalid key → 401 with `{"error":"invalid_api_key"}`
- Blank `nsbh.api.key` → no auth, request passes

**`StatelessOrchestratorTest`** (unit, mock LlmClient + ToolService):
- No tool calls → returns assistant message directly
- Single tool call round → executes tool, appends result, gets final reply
- Multiple rounds → loops correctly
- Exceeds `maxToolRounds` → stops and returns last message

**`OpenAiCompatControllerTest`** (WebTestClient integration):
- `GET /v1/models` → valid model list JSON
- `POST /v1/chat/completions` non-stream → valid ChatCompletion JSON structure
- `POST /v1/chat/completions` stream → SSE lines with correct delta format, ends with `[DONE]`
- Request without `Authorization` → 401 (when key is configured)

---

## Acceptance Criteria

- [ ] `mvn verify` 全绿（≥80% branch coverage）
- [ ] `curl -X POST http://localhost:8080/v1/chat/completions -H "Authorization: Bearer <key>" -d '{"model":"mock","messages":[{"role":"user","content":"hi"}],"stream":false}'` 返回合法 OpenAI 格式
- [ ] 同上，`"stream":true` 返回正确 SSE 流，以 `data: [DONE]` 结束
- [ ] Cherry Studio 或 Open WebUI 配置 NSBH 地址 + API key，使用 OpenRouter 免费模型能正常对话
- [ ] `nsbh.api.key` 为空时，无需鉴权即可访问 `/v1/**`
