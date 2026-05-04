package com.kp.nsbh.mcp;

import com.kp.nsbh.tools.Tool;
import reactor.core.publisher.Mono;

class McpToolAdapter implements Tool {

    private final McpClient client;
    private final String toolName;

    McpToolAdapter(McpClient client, String toolName) {
        this.client = client;
        this.toolName = toolName;
    }

    @Override
    public Mono<String> execute(String inputJson) {
        return client.callTool(toolName, inputJson);
    }
}
