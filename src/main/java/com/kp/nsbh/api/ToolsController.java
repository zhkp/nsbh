package com.kp.nsbh.api;

import com.kp.nsbh.api.dto.ToolMetadataDto;
import com.kp.nsbh.tools.ToolRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
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
    public Flux<ToolMetadataDto> listTools() {
        return Mono.fromCallable(() -> toolRegistry.listMetadata().stream()
                        .map(tool -> new ToolMetadataDto(
                                tool.name(),
                                tool.description(),
                                tool.schema(),
                                tool.requiredPermissions()
                        ))
                        .toList())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }
}
