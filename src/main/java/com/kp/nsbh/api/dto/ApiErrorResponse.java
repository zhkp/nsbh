package com.kp.nsbh.api.dto;

import java.time.Instant;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String requestId
) {
}
