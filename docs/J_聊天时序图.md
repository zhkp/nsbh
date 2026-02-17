# J. 聊天时序图

## 1) 标准聊天链路（含可选工具调用）

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant C as ConversationController
    participant S as ConversationService
    participant CR as ConversationRepository
    participant MR as MessageRepository
    participant L as LlmClient
    participant TS as ToolService
    participant TR as ToolRegistry
    participant T as Tool(time/http_get)

    U->>C: POST /api/v1/conversations/{id}/chat
    C->>S: chat(id, message, model)
    S->>CR: findById(id)
    CR-->>S: conversation
    S->>MR: save(USER, NORMAL)
    S->>S: maybeCompactMemory()
    S->>MR: findByConversationIdOrderByCreatedAtAsc()
    MR-->>S: all messages
    alt NORMAL > compactAfter
        S->>L: summarize(normals)
        L-->>S: summary text
        S->>MR: delete old SUMMARY + save new SUMMARY
    end
    S->>S: buildPromptWindow(system + summary + last N normal)
    S->>L: firstReply(userMessage, model, promptWindow)
    L-->>S: assistant text OR toolCall

    alt no toolCall
        S->>MR: save(ASSISTANT, NORMAL)
        S-->>C: ChatResult
        C-->>U: ChatResponse(requestId, assistantMessage)
    else has toolCall
        S->>TS: execute(conversationId, toolName, inputJson, toolCallId)
        TS->>TR: findMetadata/findTool
        TR-->>TS: metadata + tool
        TS->>T: execute(inputJson)
        T-->>TS: tool output
        TS-->>S: ToolExecutionResult(status/reason/result)
        S->>MR: save(TOOL, NORMAL)
        S->>S: buildPromptWindow()
        S->>L: finalReply(userMessage, model, toolResult, promptWindow)
        L-->>S: final assistant text
        S->>MR: save(ASSISTANT, NORMAL)
        S-->>C: ChatResult(+toolCalls)
        C-->>U: ChatResponse(requestId, assistantMessage, toolCalls)
    end
```

## 2) 工具执行治理点（ToolService）

执行前检查顺序：
1. 工具是否注册（`ToolRegistry`）
2. 是否在 `nsbh.tools.allowed` allowlist 中
3. 所需权限是否在 `nsbh.permissions.granted`
4. 输入大小是否超限（`maxInputBytes`）
5. 执行超时（`timeoutMs`）
6. 输出大小是否超限（`maxOutputBytes`）

审计日志：
- Logger: `TOOL_AUDIT`
- 固定字段：`requestId`, `conversationId`, `toolName`, `status`, `reason`, `durationMs`

## 3) Daily Summary 调度链路

```mermaid
sequenceDiagram
    autonumber
    participant SCH as DailySummaryScheduler
    participant MR as MessageRepository
    participant CR as ConversationRepository
    participant L as LlmClient

    SCH->>SCH: runDailySummary() (jobRunId)
    SCH->>MR: findConversationIdsWithMessagesSince(now-24h)
    MR-->>SCH: conversationIds
    loop each conversationId
        SCH->>CR: findById(conversationId)
        SCH->>MR: findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc()
        MR-->>SCH: recent messages
        alt messages not empty
            SCH->>L: summarize(messages)
            L-->>SCH: summary text
            SCH->>MR: save(SYSTEM, DAILY_SUMMARY)
        end
    end
    SCH->>SCH: log daily_summary_end
```
