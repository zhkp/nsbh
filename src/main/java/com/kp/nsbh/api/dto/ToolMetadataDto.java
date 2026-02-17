package com.kp.nsbh.api.dto;

import java.util.List;

public record ToolMetadataDto(
        String name,
        String description,
        String schema,
        List<String> requiredPermissions
) {
}
