# E. 设计模式分析

## 1. 策略模式（Strategy）
- 体现：`LlmClient` 接口 + `MockLlmClient` / `OpenAiLlmClient` 两个实现。
- 位置：
  - `src/main/java/com/kp/nsbh/agent/LlmClient.java`
  - `src/main/java/com/kp/nsbh/agent/MockLlmClient.java`
  - `src/main/java/com/kp/nsbh/agent/OpenAiLlmClient.java`
- 价值：通过配置切换模型提供方，不改业务主流程。

## 2. 注册表模式（Registry）
- 体现：`ToolRegistry` 在启动时扫描 `@NsbhTool` 并注册工具。
- 位置：
  - `src/main/java/com/kp/nsbh/tools/ToolRegistry.java`
  - `src/main/java/com/kp/nsbh/tools/NsbhTool.java`
- 价值：工具可插拔，避免硬编码 map。

## 3. 门面模式（Facade）
- 体现：`ConversationService` 作为统一业务门面，封装 LLM、工具、持久化细节。
- 位置：`src/main/java/com/kp/nsbh/agent/ConversationService.java`

## 4. 模板化流程（非继承版）
- 体现：`ConversationService.chat` 固化“保存用户消息 -> LLM -> 工具(可选) -> 最终回复 -> 保存助手消息”的流程骨架。
- 位置：`src/main/java/com/kp/nsbh/agent/ConversationService.java`

## 5. 过滤器模式（Filter）
- 体现：`RequestIdFilter` 作为 WebFlux `WebFilter`，在请求链路中注入 requestId。
- 位置：`src/main/java/com/kp/nsbh/api/RequestIdFilter.java`

## 6. 仓储模式（Repository）
- 体现：业务层通过 Reactive Repository 访问数据。
- 位置：
  - `src/main/java/com/kp/nsbh/memory/repo/ConversationRepository.java`
  - `src/main/java/com/kp/nsbh/memory/repo/MessageRepository.java`

## 7. 适配器模式（Adapter）
- 体现：`OpenAiLlmClient` 将 OpenAI Chat Completions 协议适配到内部 `LlmClient` 接口。
- 位置：`src/main/java/com/kp/nsbh/agent/OpenAiLlmClient.java`

## 8. 配置驱动模式（Configuration-driven）
- 体现：通过 `@ConditionalOnProperty` 切换 provider/scheduler 行为。
- 位置：
  - `src/main/java/com/kp/nsbh/agent/MockLlmClient.java`
  - `src/main/java/com/kp/nsbh/agent/OpenAiLlmClient.java`
  - `src/main/java/com/kp/nsbh/scheduler/DailySummaryScheduler.java`
