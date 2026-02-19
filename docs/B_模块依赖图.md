# B. 模块依赖图

## 1. 模块依赖关系图（文字）
- 启动入口：`NsbhApplication`
- HTTP 入口：`api`（WebFlux Controller）
  - 依赖 `agent`
- 业务层：`agent`
  - 依赖 `memory.repo`、`tools`、`config`
- 工具层：`tools`
  - 依赖 `config`、`logging`
- 数据层：`memory.repo -> memory.entity`
- 调度层：`scheduler`
  - 依赖 `memory.repo`、`agent.LlmClient`、`config`

抽象关系：
`api -> agent -> (tools + memory.repo)`
`scheduler -> (agent + memory.repo)`

## 2. 核心模块说明
- 核心业务模块：`agent`
  - 负责聊天编排、记忆窗口、摘要压缩。
- 核心安全模块：`tools`
  - 负责工具发现与执行治理（allowlist/permission/timeout/size/audit）。
- 核心数据模块：`memory`
  - 负责 R2DBC 持久化与查询。
- 核心接入模块：`api`
  - 负责响应模型与 requestId 对齐。

## 3. 入口模块
- 应用启动入口：`com.kp.nsbh.NsbhApplication`
- HTTP 入口：
  - `ConversationController`
  - `ToolsController`
- 定时入口：`DailySummaryScheduler.scheduledRun`
- 过滤器入口：`RequestIdFilter`（WebFilter）
