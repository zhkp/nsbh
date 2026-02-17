package com.kp.nsbh.api.dto;

import java.util.UUID;

public record CreateConversationResponse(
        UUID conversationId,
        String requestId
) {
}
