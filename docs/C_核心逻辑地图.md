# C. 核心逻辑地图（Controller -> Service -> Repository）

## 1. 创建会话链路
- Controller: `ConversationController.createConversation`
- Service: `ConversationService.createConversation`
- Repository: `ConversationRepository.save`
- 结果：返回 `conversationId + requestId`

## 2. 发送消息聊天链路
- Controller: `ConversationController.chat`
- Service: `ConversationService.chat`
  1. `ConversationRepository.findById` 校验会话。
  2. `MessageRepository.save` 写 USER 消息。
  3. `maybeCompactMemory`：
     - `findByConversationIdOrderByCreatedAtAsc`
     - NORMAL 消息超过 `compactAfter` 后，调用 `LlmClient.summarize`
     - 删除旧 `SUMMARY` 并写入新 `SUMMARY`
  4. `buildPromptWindow`：
     - 组装 `system prompt + latest summary + last N normal messages`
  5. `LlmClient.firstReply`。
  6. 若有工具调用：
     - `ToolService.execute`（allowlist、permission、输入输出大小、timeout）
     - `MessageRepository.save` 写 TOOL 消息
     - `LlmClient.finalReply` 生成最终回复
  7. `MessageRepository.save` 写 ASSISTANT 消息
- 返回：`Mono<ChatResult>`，Controller 转为 `Mono<ChatResponse>`

## 3. 查询消息链路
- Controller: `ConversationController.messages`
- Service: `ConversationService.getMessages`
- Repository:
  - `ConversationRepository.existsById`
  - `MessageRepository.findByConversationIdOrderByCreatedAtAsc`
- 结果：按时间升序返回完整消息列表

## 4. 工具清单链路
- Controller: `ToolsController.listTools`
- Service/Registry: `ToolRegistry.listMetadata`
- 数据来源：`@NsbhTool` 注解扫描自动注册

## 5. 每日摘要调度链路
- Scheduler: `DailySummaryScheduler.scheduledRun/runDailySummary`
- Repository:
  - `MessageRepository.findConversationIdsWithMessagesSince`
  - `ConversationRepository.findById`
  - `MessageRepository.findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc`
  - `MessageRepository.save` 写入 `DAILY_SUMMARY`
- LLM: `LlmClient.summarize`
- 返回：`Mono<Void>`，`scheduledRun` 中订阅触发执行
