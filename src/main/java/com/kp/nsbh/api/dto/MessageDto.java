package com.kp.nsbh.api.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageDto(
        UUID id,
        String role,
        String type,
        String content,
        String toolName,
        String toolCallId,
        Instant createdAt
) {
}
