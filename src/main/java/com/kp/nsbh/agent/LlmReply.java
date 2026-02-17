package com.kp.nsbh.agent;

public record LlmReply(
        String assistantMessage,
        ToolCallRequest toolCall
) {
}
