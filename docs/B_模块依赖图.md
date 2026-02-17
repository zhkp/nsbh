# B. 模块依赖图

## 1. 模块依赖关系图（文字）
- 入口：`NsbhApplication`
- 接口层：`api`
  - 依赖：`agent`
- 业务编排层：`agent`
  - 依赖：`tools`, `memory.repo`, `config`
- 工具执行层：`tools`
  - 依赖：`config`, `logging`
- 数据层：`memory.repo` -> `memory.entity`
- 调度层：`scheduler`
  - 依赖：`agent.LlmClient`, `memory.repo`, `config`
- 基础设施层：`config`, `logging`, `resources`（Flyway、logback）

可以抽象为：
`api -> agent -> (tools + memory.repo) -> memory.entity`
`scheduler -> (agent + memory.repo)`

## 2. 核心模块说明
- 核心业务模块：`agent`
  - 负责聊天主流程和上下文管理。
- 核心安全模块：`tools`
  - 负责工具权限、allowlist、超时与大小限制、审计。
- 核心数据模块：`memory`
  - 负责会话/消息持久化与查询。
- 核心接入模块：`api`
  - 负责 REST 暴露与响应模型。

## 3. 入口模块
- 应用启动入口：`com.kp.nsbh.NsbhApplication`
- HTTP 入口：
  - `ConversationController`
  - `ToolsController`
- 定时入口：`DailySummaryScheduler.scheduledRun`
- 过滤器入口：`RequestIdFilter`
