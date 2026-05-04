package com.kp.nsbh.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.tools.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class McpServerRegistryTest {

    @Test
    void emptyServersRegistersNothing() {
        NsbhProperties props = new NsbhProperties();
        ToolRegistry registry = new ToolRegistry(List.of());
        McpServerRegistry srv = new McpServerRegistry(
                registry, props, new ObjectMapper(), WebClient.builder());

        srv.run(null);

        assertEquals(0, registry.listMetadata().size());
    }

    @Test
    void toolsFromMockClientRegisteredInToolRegistry() {
        McpClient mockClient = mock(McpClient.class);
        when(mockClient.listTools()).thenReturn(Mono.just(List.of(
                new McpToolDefinition("remote_tool", "does remote stuff", "{}")
        )));

        ToolRegistry registry = new ToolRegistry(List.of());
        McpServerRegistry srv = new McpServerRegistry(
                registry, new NsbhProperties(), new ObjectMapper(), WebClient.builder());

        srv.registerFromClient(mockClient, "test-server");

        assertNotNull(registry.findTool("remote_tool"));
        assertEquals("remote_tool", registry.findMetadata("remote_tool").name());
        assertEquals("does remote stuff", registry.findMetadata("remote_tool").description());
    }

    @Test
    void registerFromClientWhenListToolsThrowsSkipsRegistration() {
        McpClient mockClient = mock(McpClient.class);
        when(mockClient.listTools()).thenReturn(reactor.core.publisher.Mono.error(
                new McpException("connection refused")));

        ToolRegistry registry = new ToolRegistry(List.of());
        McpServerRegistry srv = new McpServerRegistry(
                registry, new NsbhProperties(), new ObjectMapper(), WebClient.builder());

        srv.registerFromClient(mockClient, "broken-server");

        assertEquals(0, registry.listMetadata().size());
    }

    @Test
    void runWithServerConfigConnectsAndRegisters() {
        NsbhProperties props = new NsbhProperties();
        NsbhProperties.McpServerConfig cfg = new NsbhProperties.McpServerConfig();
        cfg.setName("unreachable");
        cfg.setUrl("http://localhost:19999/sse");
        props.getMcp().getServers().add(cfg);

        ToolRegistry registry = new ToolRegistry(List.of());
        McpServerRegistry srv = new McpServerRegistry(
                registry, props, new ObjectMapper(), WebClient.builder());

        srv.run(null);

        assertEquals(0, registry.listMetadata().size());
    }

    @Test
    void registerFromClientWithNullMonoTreatsAsEmptyList() {
        McpClient mockClient = mock(McpClient.class);
        when(mockClient.listTools()).thenReturn(reactor.core.publisher.Mono.empty());

        ToolRegistry registry = new ToolRegistry(List.of());
        McpServerRegistry srv = new McpServerRegistry(
                registry, new NsbhProperties(), new ObjectMapper(), WebClient.builder());

        srv.registerFromClient(mockClient, "null-server");

        assertEquals(0, registry.listMetadata().size());
    }
}
