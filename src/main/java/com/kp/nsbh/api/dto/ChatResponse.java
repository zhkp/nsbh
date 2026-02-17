package com.kp.nsbh.api.dto;

import java.util.List;
import java.util.UUID;

public record ChatResponse(
        UUID conversationId,
        String assistantMessage,
        List<ToolCallResultDto> toolCalls,
        String requestId
) {
}
