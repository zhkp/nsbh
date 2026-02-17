package com.kp.nsbh.api.dto;

public record ToolCallResultDto(
        String toolName,
        String status,
        String reason,
        String result
) {
}
