# C. 核心逻辑地图（Controller -> Service -> Repository）

## 1. 创建会话链路
- Controller: `ConversationController.createConversation`
- Service: `ConversationService.createConversation`
- Repository: `ConversationRepository.save`
- 结果：返回 `conversationId + requestId`

## 2. 发送消息聊天链路
- Controller: `ConversationController.chat`
- Service: `ConversationService.chat`
  1. `ConversationRepository.findById` 校验会话存在。
  2. `MessageRepository.save` 保存 USER 消息。
  3. `maybeCompactMemory`：
     - `MessageRepository.countByConversationIdAndType`
     - 过阈值时 `MessageRepository.findByConversationIdAndTypeOrderByCreatedAtAsc`
     - `LlmClient.summarize`
     - `MessageRepository.save` 写入/更新 `SUMMARY`。
  4. `buildPromptWindow`：
     - 加系统提示
     - `MessageRepository.findByConversationIdAndTypeOrderByCreatedAtDesc` 取最新 SUMMARY
     - `MessageRepository.findByConversationIdAndTypeOrderByCreatedAtAsc` 取 NORMAL 窗口
  5. `LlmClient.firstReply`。
  6. 若 LLM 请求工具：
     - Service -> `ToolService.execute`
       - `ToolRegistry.findMetadata/findTool`
       - allowlist/permissions/input-size/timeout/output-size
       - 返回 `ToolExecutionResult`
     - `MessageRepository.save` 保存 TOOL 消息
     - 再 `LlmClient.finalReply`
  7. `MessageRepository.save` 保存 ASSISTANT 消息
- 返回：`ChatResult` -> Controller 映射为 `ChatResponse`

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
