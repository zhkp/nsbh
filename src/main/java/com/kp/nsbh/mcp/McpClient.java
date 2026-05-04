package com.kp.nsbh.mcp;

import java.util.List;
import reactor.core.publisher.Mono;

public interface McpClient {
    Mono<List<McpToolDefinition>> listTools();
    Mono<String> callTool(String name, String inputJson);
    Mono<Void> close();
}
