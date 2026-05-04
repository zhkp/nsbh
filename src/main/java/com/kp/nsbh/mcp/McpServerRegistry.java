package com.kp.nsbh.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.tools.ToolMetadata;
import com.kp.nsbh.tools.ToolRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class McpServerRegistry implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpServerRegistry.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final ToolRegistry toolRegistry;
    private final NsbhProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private final List<McpClient> clients = new ArrayList<>();

    public McpServerRegistry(ToolRegistry toolRegistry,
                              NsbhProperties properties,
                              ObjectMapper objectMapper,
                              WebClient.Builder webClientBuilder) {
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (NsbhProperties.McpServerConfig cfg : properties.getMcp().getServers()) {
            try {
                McpClient client = new McpSseClient(webClientBuilder, cfg, objectMapper);
                registerFromClient(client, cfg.getName());
                clients.add(client);
            } catch (Exception e) {
                log.warn("Failed to connect to MCP server '{}': {}", cfg.getName(), e.getMessage());
            }
        }
    }

    void registerFromClient(McpClient client, String serverName) {
        List<McpToolDefinition> tools;
        try {
            tools = client.listTools().block(CONNECT_TIMEOUT);
            if (tools == null) tools = List.of();
        } catch (Exception e) {
            log.warn("Failed to list tools from MCP server '{}': {}", serverName, e.getMessage());
            return;
        }
        for (McpToolDefinition def : tools) {
            ToolMetadata meta = new ToolMetadata(
                    def.name(), def.description(), def.inputSchemaJson(), List.of());
            toolRegistry.register(meta, new McpToolAdapter(client, def.name()));
        }
        log.info("MCP server '{}' registered {} tools", serverName, tools.size());
    }

    @PreDestroy
    public void shutdown() {
        clients.forEach(c -> c.close().subscribe());
    }
}
