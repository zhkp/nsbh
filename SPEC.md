# Nanobot Spring Boot Host (NSBH) — SPEC

## 0. Goal
Build a lightweight Spring Boot application that hosts a “Nanobot-style” agent:
- Exposes a simple chat API
- Supports tool calling with a strict permission model
- Persists conversation memory
- Supports scheduled tasks

The emphasis is: **clarity, debuggability, minimal abstraction**, and **secure tool execution**.

## 1. Non-goals (for v1)
- Multi-agent orchestration / agent swarms
- Plugin marketplace / dynamic code loading
- Distributed execution / microservices
- Full feature parity with Python Nanobot

## 2. Tech Stack
- Java 21 (or 17 if you prefer), Spring Boot 3.x
- Maven
- JSON: Jackson
- Persistence: H2 (dev) + optional Postgres profile
- Optional: Redis (future milestone, not required for v1)
- API Docs: springdoc-openapi (Swagger UI)
- HTTP Client: Spring WebClient
- Config: application.yml + env vars

## 3. Domain Model
### 3.1 Core types
- Conversation
  - id (UUID)
  - createdAt, updatedAt
- Message
  - id (UUID)
  - conversationId
  - role: SYSTEM | USER | ASSISTANT | TOOL
  - type: NORMAL | SUMMARY
  - content (text)
  - toolName (nullable)
  - toolCallId (nullable)
  - createdAt
- Tool
  - name (string)
  - description (string)
  - schema (JSON Schema-like metadata)
  - permission requirements

### 3.2 Tool permission model (MUST)
Default deny.
Each tool declares required permissions:
- NET_HTTP (outbound http/https)
- FS_READ (read-only file access)
- FS_WRITE (write access)
- SHELL (execute commands)  **DISABLED IN v1** (no shell tools)

Enforcement:
- Global allowlist in config: `tools.allowed: [time, http_get, ...]`
- Per-tool required permissions must be satisfied by config `permissions.granted: [NET_HTTP, ...]`
- If not allowed or permissions missing => tool call is rejected with a structured error message.

Safety requirements:
- Tool execution must have:
  - timeout (default 3s, configurable)
  - max payload size for inputs/outputs
  - structured audit logs (tool name, args hash, duration, status)

## 4. API Design
Base path: `/api/v1`

### 4.1 Create conversation
POST `/conversations`
Response:
- conversationId

### 4.2 Chat (send a message)
POST `/conversations/{id}/chat`
Request:
{
  "message": "string",
  "model": "gpt-4.1-mini | gpt-4.1 | ... (config default allowed)"
}

Response:
{
  "conversationId": "...",
  "assistantMessage": "string",
  "toolCalls": [
    {
      "toolName": "time",
      "status": "SUCCESS|REJECTED|FAILED",
      "result": "string"
    }
  ]
}

Rules:
- Append user message to memory
- Run agent loop:
  - build prompt from system + recent messages (last N)
  - call LLM
  - if tool call requested, validate allowlist + permissions + schema
  - run tool with timeout
  - append tool result to memory
  - call LLM again to produce final assistant message
- Max tool call rounds: 2 (configurable)

### 4.3 Get conversation messages
GET `/conversations/{id}/messages`

### 4.4 List tools
GET `/tools`
Returns tool name + description + schema + permissions

## 5. LLM Provider (v1)
Implement `LlmClient` interface with one provider:
- OpenAI Chat Completions (or Responses API if you prefer)

Config:
- `llm.provider=openai`
- `llm.apiKey` from env
- `llm.baseUrl` default official
- `llm.modelDefault`

Requirements:
- request/response logging WITHOUT leaking API keys
- graceful error mapping to API responses

## 6. Tools (v1)
Implement at least:
1) `time`
- returns current server time in ISO-8601 + timezone

2) `http_get` (optional in Milestone 2; can be delayed)
- input: { "url": "https://..." }
- only allow http/https
- deny private IP ranges (basic SSRF protection)
- max response size limit
- timeout 3s

## 7. Memory & Persistence (v1)
- Store conversations & messages in DB (H2)
- Use Spring Data JPA
- Prompt uses last N messages (config: `memory.window=20`)
- Memory compaction (Milestone 3 Step 1):
  - Config: `memory.compactAfter=40`
  - When NORMAL messages exceed threshold, call LLM summarize
  - Persist summary as SYSTEM message with type `SUMMARY` (update latest summary)
  - Prompt uses: system prompt + latest summary + last N NORMAL messages
- Provide schema migrations (Flyway optional)

## 8. Scheduler (v1.5 / Milestone 3)
Provide a minimal scheduled task system:
- A scheduled job `daily_summary` (disabled by default)
- When enabled, at 09:00 local time:
  - summarize yesterday’s conversations (or last 24h)
  - store summary as a SYSTEM message per conversation

Config:
- `scheduler.dailySummary.enabled=false`
- `scheduler.dailySummary.cron=0 0 9 * * *`

## 9. Observability
- Structured logs (JSON preferred)
- Include requestId (trace id) per request
- Tool audit logs separate logger name: `TOOL_AUDIT`

## 10. Testing (MUST)
- Unit tests:
  - tool permission enforcement
  - tool timeout behavior
  - prompt window selection
- Integration tests:
  - POST chat returns 200
  - messages persisted
Mock LLM client for tests.

## 11. Milestones & Acceptance Criteria

### Milestone 1 — Minimal runnable
Deliver:
- Spring Boot app runs
- POST create conversation
- POST chat uses MockLlmClient (no real API key needed)
- Tool: `time`
Acceptance:
- `mvn test` passes
- `curl` chat returns assistant message
- messages stored and retrievable

### Milestone 2 — Real LLM + Tool registry
Deliver:
- OpenAI LLM client implementation behind interface
- Tool registry via Spring beans + annotation scanning
- List tools endpoint
- Permission model enforced + audit logs
Acceptance:
- With `OPENAI_API_KEY` set, chat works end-to-end
- Forbidden tool call returns REJECTED with reason

### Milestone 3 — Memory + Scheduler
Deliver:
- JPA persistence finalized
- Daily summary scheduled task behind config flag
Acceptance:
- Enable flag => summary messages appear
- Disable flag => no scheduled execution

## 12. Repo Layout (suggested)
- src/main/java/.../api (controllers, dto)
- src/main/java/.../agent (prompting, loop)
- src/main/java/.../llm (client interface + providers)
- src/main/java/.../tools (tool interfaces + implementations)
- src/main/java/.../memory (repositories, entities)
- src/test/java/... (unit + integration)

## 13. Security Notes
- No shell execution in v1
- Avoid SSRF in http_get
- Do not log secrets
- Limit tool output size
