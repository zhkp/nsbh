package com.kp.nsbh.agent;

import java.util.List;

public record LlmReply(String assistantMessage, List<ToolCallRequest> toolCalls) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public static LlmReply text(String message) {
        return new LlmReply(message, List.of());
    }

    public static LlmReply withTool(ToolCallRequest toolCall) {
        return new LlmReply(null, List.of(toolCall));
    }

    public static LlmReply withTools(List<ToolCallRequest> toolCalls) {
        return new LlmReply(null, List.copyOf(toolCalls));
    }
}
