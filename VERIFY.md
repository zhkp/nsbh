# Verify Milestone 3 - Daily Summary Scheduler

## 1) Run tests

```bash
mvn test
```

## 2) Enable scheduler and run app

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock --scheduler.dailySummary.enabled=true"
```

## 3) Create conversation + some chats

```bash
BASE=http://localhost:8080
CID=$(curl -s -X POST "$BASE/api/v1/conversations" | jq -r '.conversationId')

curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"hello day"}' | jq

curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"what time"}' | jq
```

## 4) Wait for cron (or call scheduler method in test)

In integration test, scheduler is verified via direct method call `runDailySummary()`.
At runtime, scheduler uses `scheduler.dailySummary.cron`.

## 5) Verify DAILY_SUMMARY persisted

```bash
curl -s "$BASE/api/v1/conversations/$CID/messages" | jq
```

Expected at least one message with:
- `role = "SYSTEM"`
- `type = "DAILY_SUMMARY"`

## 6) Verify scheduler logs include jobRunId

Look for logs like:

```text
jobRunId=... event=daily_summary_start conversationCount=...
jobRunId=... event=daily_summary_saved conversationId=... messageCount=...
jobRunId=... event=daily_summary_end
```

## 7) Disable scheduler and verify no execution

Start app with scheduler disabled (even with high-frequency cron):

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock --scheduler.dailySummary.enabled=false --scheduler.dailySummary.cron=*/1 * * * * *"
```

Create a conversation and send one chat:

```bash
BASE=http://localhost:8080
CID=$(curl -s -X POST "$BASE/api/v1/conversations" | jq -r '.conversationId')
curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"hello"}' | jq
sleep 2
curl -s "$BASE/api/v1/conversations/$CID/messages" | jq
```

Expected:
- no message with `type = "DAILY_SUMMARY"`
- no daily summary job logs (`event=daily_summary_start` / `event=daily_summary_end`)

## 8) Verify `http_get` tool (SPEC optional item, continued)

Enable tool + permission:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock --nsbh.tools.allowed[0]=time --nsbh.tools.allowed[1]=http_get --nsbh.permissions.granted[0]=NET_HTTP"
```

Call chat with a URL (Mock LLM will deterministically request `http_get`):

```bash
BASE=http://localhost:8080
CID=$(curl -s -X POST "$BASE/api/v1/conversations" | jq -r '.conversationId')
curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"please fetch https://example.com"}' | jq
```

Expected:
- `toolCalls[0].toolName = "http_get"`
- `toolCalls[0].status` is `SUCCESS` (or `FAILED` if outbound network unavailable in your environment)
- `/api/v1/tools` includes `http_get` and `requiredPermissions=["NET_HTTP"]`
- `http_get` will reject redirects (3xx) and private/loopback hosts

Permission rejection example (without `NET_HTTP`):

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock --nsbh.tools.allowed[0]=time --nsbh.tools.allowed[1]=http_get"
```

Then call:

```bash
curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"please fetch https://example.com"}' | jq
```

Expected:
- `toolCalls[0].status = "REJECTED"`
- `toolCalls[0].reason = "PERMISSION_MISSING"`

## 9) Verify OpenAPI docs

`springdoc` is enabled by dependency. Start app and open:

```bash
curl -s "http://localhost:8080/v3/api-docs" | jq '.paths | keys'
```

Expected includes:
- `/api/v1/conversations`
- `/api/v1/conversations/{id}/chat`
- `/api/v1/conversations/{id}/messages`
- `/api/v1/tools`

Swagger UI URL:
- `http://localhost:8080/swagger-ui/index.html`

## 10) Verify Flyway migration

On startup, logs should contain Flyway migration lines (e.g. `Migrating schema`).
You can also verify tables exist via H2 console or SQL query.

H2 check:

```bash
curl -s "http://localhost:8080/h2-console" >/dev/null
```

In H2 console, run:

```sql
select table_name from information_schema.tables
where table_schema = 'PUBLIC' and table_name in ('CONVERSATIONS', 'MESSAGES');
```

## 11) Verify Postgres profile (optional)

Run with Postgres profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=postgres \
  -Dspring-boot.run.arguments="--POSTGRES_URL=jdbc:postgresql://localhost:5432/nsbh --POSTGRES_USER=nsbh --POSTGRES_PASSWORD=nsbh --nsbh.llm.provider=mock"
```

Expected:
- app starts with PostgreSQL datasource
- Flyway applies `V1__init_schema.sql`
- existing APIs behave the same as H2 mode

Postgres integration test (Testcontainers, Docker required):

```bash
mvn -Dtest=PostgresIntegrationTest test
```

Expected:
- starts a `postgres:16-alpine` container
- Flyway migration row exists in `flyway_schema_history`
- conversation/chat API works on PostgreSQL
- if Docker is unavailable, this test is skipped (`disabledWithoutDocker=true`)

## 12) Verify structured JSON logs

All application logs now output JSON (via `logback-spring.xml`), including Spring startup logs and `TOOL_AUDIT`.

Trigger a tool call:

```bash
BASE=http://localhost:8080
CID=$(curl -s -X POST "$BASE/api/v1/conversations" | jq -r '.conversationId')
curl -s -X POST "$BASE/api/v1/conversations/$CID/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"what time is it?"}' | jq
```

Expected log line example:

```json
{"timestamp":"...","level":"INFO","logger":"TOOL_AUDIT","thread":"...","message":"{\"requestId\":\"...\",\"conversationId\":\"...\",\"toolName\":\"time\",\"status\":\"SUCCESS\",\"reason\":\"NONE\",\"durationMs\":1}","requestId":"..."}
```

Scheduler logs are also JSON, for example:

```json
{"timestamp":"...","level":"INFO","logger":"com.kp.nsbh.scheduler.DailySummaryScheduler","message":"{\"event\":\"daily_summary_start\",\"jobRunId\":\"...\",\"conversationCount\":1}"}
```
