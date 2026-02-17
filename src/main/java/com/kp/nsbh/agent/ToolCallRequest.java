package com.kp.nsbh.agent;

public record ToolCallRequest(
        String id,
        String toolName,
        String inputJson
) {
}
