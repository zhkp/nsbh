# I. 配置速查表

基于当前代码与 `application.yml` / `application-postgres.yml`。

## 1) 运行与端口

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | `8080` | HTTP 端口 |
| `server.error.include-message` | `always` | 错误响应中包含 message |

## 2) LLM 配置（`nsbh.llm.*`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `nsbh.llm.provider` | `mock` | 提供方：`mock` 或 `openai` |
| `nsbh.llm.modelDefault` | `gpt-4.1-mini` | 默认模型名 |
| `nsbh.llm.baseUrl` | `https://api.openai.com` | OpenAI 基础地址 |
| `nsbh.llm.apiKey` | `${OPENAI_API_KEY:}` | API Key（可用启动参数覆盖） |
| `nsbh.llm.timeoutMs` | `15000` | LLM 请求超时（毫秒） |

常用启动示例：

```bash
# mock
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock"

# openai（直接参数传 key）
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=openai --nsbh.llm.apiKey=<YOUR_OPENAI_API_KEY> --nsbh.llm.timeoutMs=30000"
```

## 3) Memory 配置（`nsbh.memory.*`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `nsbh.memory.window` | `20` | Prompt 窗口的 NORMAL 消息条数 |
| `nsbh.memory.compactAfter` | `40` | 超过阈值触发 summary compaction |
| `nsbh.memory.systemPrompt` | 固定字符串 | 系统提示词 |

## 4) Tools 配置（`nsbh.tools.*`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `nsbh.tools.timeoutMs` | `3000` | 工具执行超时（毫秒） |
| `nsbh.tools.maxInputBytes` | `8192` | 工具输入大小限制 |
| `nsbh.tools.maxOutputBytes` | `32768` | 工具输出大小限制 |
| `nsbh.tools.allowed` | `["time"]` | 工具 allowlist（默认拒绝其他工具） |

权限配置：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `nsbh.permissions.granted` | 空 | 全局授予权限列表，例如 `NET_HTTP` |

`http_get` 启用示例：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock --nsbh.tools.allowed[0]=time --nsbh.tools.allowed[1]=http_get --nsbh.permissions.granted[0]=NET_HTTP"
```

## 5) Scheduler 配置（`scheduler.dailySummary.*`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `scheduler.dailySummary.enabled` | `false` | 是否启用 daily summary 定时任务 |
| `scheduler.dailySummary.cron` | `0 0 9 * * *` | cron 表达式 |

启用示例：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--nsbh.llm.provider=mock --scheduler.dailySummary.enabled=true --scheduler.dailySummary.cron=*/30 * * * * *"
```

## 6) 数据库与 Profile

默认（H2 + R2DBC + JDBC Flyway）：
- `spring.r2dbc.url=r2dbc:h2:mem:///nsbh...`
- `spring.datasource.url=jdbc:h2:mem:///nsbh...`

Postgres profile：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=postgres \
  -Dspring-boot.run.arguments="--POSTGRES_URL=jdbc:postgresql://localhost:5432/nsbh --POSTGRES_R2DBC_URL=r2dbc:postgresql://localhost:5432/nsbh --POSTGRES_USER=nsbh --POSTGRES_PASSWORD=nsbh --nsbh.llm.provider=mock"
```

## 7) 调试与验证建议

- 全量测试：`mvn test`
- 接口验收：参考 `VERIFY.md`
- OpenAPI：`/v3/api-docs`、`/swagger-ui/index.html`
- requestId：响应头 `X-Request-Id` + 响应体 `requestId`
