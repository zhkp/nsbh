package com.kp.nsbh.tools;

import java.util.List;

public record ToolMetadata(
        String name,
        String description,
        String schema,
        List<String> requiredPermissions
) {
}
