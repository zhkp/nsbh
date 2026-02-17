package com.kp.nsbh.api;

import com.kp.nsbh.api.dto.ToolMetadataDto;
import com.kp.nsbh.tools.ToolRegistry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools")
public class ToolsController {
    private final ToolRegistry toolRegistry;

    public ToolsController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @GetMapping
    public List<ToolMetadataDto> listTools() {
        return toolRegistry.listMetadata().stream()
                .map(tool -> new ToolMetadataDto(
                        tool.name(),
                        tool.description(),
                        tool.schema(),
                        tool.requiredPermissions()
                ))
                .toList();
    }
}
