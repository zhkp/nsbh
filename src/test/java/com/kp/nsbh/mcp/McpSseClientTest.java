package com.kp.nsbh.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class McpSseClientTest {

    private McpSseClient client(ExchangeFunction exchange) {
        NsbhProperties.McpServerConfig cfg = new NsbhProperties.McpServerConfig();
        cfg.setName("test");
        cfg.setUrl("http://mcp-server/sse");
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
        return new McpSseClient(builder, cfg, new ObjectMapper());
    }

    private ExchangeFunction sseResponse(String... dataLines) {
        StringBuilder body = new StringBuilder();
        for (String line : dataLines) {
            body.append("data: ").append(line).append("\n\n");
        }
        return req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .body(body.toString())
                .build());
    }

    @Test
    void listToolsParsesToolDefinitions() {
        String rpcResponse = """
                {"jsonrpc":"2.0","id":"x","result":{"tools":[
                  {"name":"my_tool","description":"does stuff","inputSchema":{"type":"object"}}
                ]}}
                """.replace("\n", "");

        List<McpToolDefinition> tools = client(sseResponse(rpcResponse))
                .listTools().block();

        assertEquals(1, tools.size());
        assertEquals("my_tool", tools.get(0).name());
        assertEquals("does stuff", tools.get(0).description());
    }

    @Test
    void callToolReturnsTextContent() {
        String rpcResponse = """
                {"jsonrpc":"2.0","id":"x","result":{"content":[
                  {"type":"text","text":"the answer"}
                ]}}
                """.replace("\n", "");

        String result = client(sseResponse(rpcResponse))
                .callTool("my_tool", "{}").block();

        assertEquals("the answer", result);
    }

    @Test
    void jsonRpcErrorMapsToMcpException() {
        String rpcResponse = """
                {"jsonrpc":"2.0","id":"x","error":{"code":-32601,"message":"method not found"}}
                """.replace("\n", "");

        assertThrows(McpException.class,
                () -> client(sseResponse(rpcResponse)).listTools().block());
    }
}
