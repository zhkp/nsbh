package com.kp.nsbh.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String message,
        String model
) {
}
