package com.kp.nsbh.tools;

public record ToolExecutionResult(
        String toolName,
        ToolCallStatus status,
        ToolCallReason reason,
        String result,
        String toolCallId
) {
}
