# NSBH WebFlux 验收手册

本文按 `SPEC.md` 的里程碑顺序给出验收步骤：`W1 -> W2 -> W3 -> W4`。

## 0) 通用前置

```bash
BASE=http://localhost:8080
```

依赖：
- `jq`
- Java 21
- Maven

---

## W1 - Reactive Baseline

### 1) 测试

```bash
mvn test
```

预期：
- `BUILD SUCCESS`
- `RequestIdIntegrationTest` 通过
- `ConversationControllerIntegrationTest` 通过

### 2) 启动（mock provider）

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock"
```

### 3) 验证基础 API

```bash
CID=$(curl -s -X POST "$BASE/api/v1/conversations" | jq -r '.conversationId')

curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"what time is it?"}' | jq

curl -s "$BASE/api/v1/conversations/$CID/messages" | jq
```

预期：
- 三个接口都返回成功
- chat 响应包含 `requestId`
- messages 中可见 USER/ASSISTANT（可能含 TOOL）

### 4) 验证 tools 列表

```bash
curl -s "$BASE/api/v1/tools" | jq
```

预期：
- 返回 `time`（以及当前已启用工具）
- 包含 `requiredPermissions`

### 5) 验证 requestId header/body

```bash
curl -i -s -X POST "$BASE/api/v1/conversations" | sed -n '1,20p'
```

预期：
- 响应头有 `X-Request-Id`
- 响应 JSON 有 `requestId`

---

## W2 - Reactive Persistence (R2DBC)

### 1) 测试

```bash
mvn test
```

预期：
- `BUILD SUCCESS`
- `PostgresIntegrationTest` 在无 Docker 环境下可 `Skipped`（正常）

### 2) H2 + R2DBC 验证

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock"
```

```bash
CID=$(curl -s -X POST "$BASE/api/v1/conversations" | jq -r '.conversationId')

curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"hello reactive persistence"}' | jq

curl -s "$BASE/api/v1/conversations/$CID/messages" | jq
```

预期：
- 会话消息可持久化并可读回

### 3) Postgres profile（可选）

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=postgres \
  -Dspring-boot.run.arguments="--POSTGRES_URL=jdbc:postgresql://localhost:5432/nsbh --POSTGRES_R2DBC_URL=r2dbc:postgresql://localhost:5432/nsbh --POSTGRES_USER=nsbh --POSTGRES_PASSWORD=nsbh --nsbh.llm.provider=mock"
```

预期：
- 应用能启动，接口行为与 H2 一致

---

## W3 - Reactive Tooling & LLM

### 1) 测试

```bash
mvn test
```

预期：
- `LlmProviderSelectionMockTest` / `LlmProviderSelectionOpenAiTest` 通过
- `ToolServiceTest` 的 `NOT_ALLOWED` / `PERMISSION_MISSING` / `TIMEOUT` 场景通过

### 2) Provider 切换：mock

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock"
```

```bash
CID=$(curl -s -X POST "$BASE/api/v1/conversations" | jq -r '.conversationId')
curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"what time is it?"}' | jq
```

预期：
- chat 成功
- 可见 toolCalls（mock 的确定性行为）

### 3) Provider 切换：openai（可选）

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=openai --nsbh.llm.apiKey=<YOUR_OPENAI_API_KEY> --nsbh.llm.timeoutMs=30000"
```

预期：
- 可通过参数直接传 `nsbh.llm.apiKey`（不依赖环境变量）
- key/额度异常时错误映射明确，日志不泄露 secret

### 4) allowlist 拒绝 (`NOT_ALLOWED`)

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock --nsbh.tools.allowed[0]=http_get"
```

```bash
CID=$(curl -s -X POST "$BASE/api/v1/conversations" | jq -r '.conversationId')
curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"what time is it?"}' | jq
```

预期：
- `toolCalls[0].status == "REJECTED"`
- `toolCalls[0].reason == "NOT_ALLOWED"`

### 5) permission 拒绝 (`PERMISSION_MISSING`)

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock --nsbh.tools.allowed[0]=http_get"
```

```bash
CID=$(curl -s -X POST "$BASE/api/v1/conversations" | jq -r '.conversationId')
curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"please fetch https://example.com"}' | jq
```

预期：
- `toolCalls[0].status == "REJECTED"`
- `toolCalls[0].reason == "PERMISSION_MISSING"`

### 6) timeout (`TIMEOUT`)

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock --nsbh.tools.allowed[0]=http_get --nsbh.permissions.granted[0]=NET_HTTP --nsbh.tools.timeoutMs=1"
```

```bash
CID=$(curl -s -X POST "$BASE/api/v1/conversations" | jq -r '.conversationId')
curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"please fetch https://example.com"}' | jq
```

预期：
- `toolCalls[0].status == "FAILED"`
- `toolCalls[0].reason == "TIMEOUT"`

---

## W4 - Memory & Scheduler 验收收尾

### 1) 测试

```bash
mvn test
```

预期：
- `SummaryCompactionIntegrationTest` 通过
- `DailySummarySchedulerIntegrationTest` / `DailySummarySchedulerDisabledIntegrationTest` 通过

### 2) Summary compaction 验证

以更容易触发的阈值启动：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock --nsbh.memory.compactAfter=2"
```

连续发送多轮消息后查询：

```bash
CID=$(curl -s -X POST "$BASE/api/v1/conversations" | jq -r '.conversationId')
curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" -H "Content-Type: application/json" -d '{"message":"m1"}' | jq
curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" -H "Content-Type: application/json" -d '{"message":"m2"}' | jq
curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" -H "Content-Type: application/json" -d '{"message":"m3"}' | jq
curl -s "$BASE/api/v1/conversations/$CID/messages" | jq
```

预期：
- 出现 `role="SYSTEM"` 且 `type="SUMMARY"` 的消息（或 summary 字段策略对应落库结果）

### 3) Daily summary scheduler 启用验证

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock --scheduler.dailySummary.enabled=true --scheduler.dailySummary.cron=*/30 * * * * *"
```

创建会话并发送消息，等待一个调度周期后检查：

```bash
CID=$(curl -s -X POST "$BASE/api/v1/conversations" | jq -r '.conversationId')
curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" -H "Content-Type: application/json" -d '{"message":"hello daily"}' | jq
sleep 35
curl -s "$BASE/api/v1/conversations/$CID/messages" | jq
```

预期：
- 出现 `role="SYSTEM"` 且 `type="DAILY_SUMMARY"` 的消息
- 日志包含 `jobRunId`，并有 `daily_summary_start` / `daily_summary_saved` / `daily_summary_end`

### 4) Daily summary scheduler 禁用验证

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock --scheduler.dailySummary.enabled=false --scheduler.dailySummary.cron=*/1 * * * * *"
```

创建会话并等待：

```bash
CID=$(curl -s -X POST "$BASE/api/v1/conversations" | jq -r '.conversationId')
curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" -H "Content-Type: application/json" -d '{"message":"hello"}' | jq
sleep 2
curl -s "$BASE/api/v1/conversations/$CID/messages" | jq
```

预期：
- 不会新增 `DAILY_SUMMARY`
- 日志不出现 daily summary job 执行事件

### 5) W4 验收通过标准（最终）

以下全部满足即可判定 W4 验收完成：
- `mvn test` 为 `BUILD SUCCESS`
- summary compaction 可触发且能持久化 summary
- scheduler 在 `enabled=true` 时能产出 `DAILY_SUMMARY`
- scheduler 在 `enabled=false` 时不执行
- 日志含 `requestId` 与 `jobRunId`，TOOL_AUDIT 字段固定
