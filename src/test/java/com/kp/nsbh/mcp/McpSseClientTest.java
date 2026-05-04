package com.kp.nsbh.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import java.util.List;
import java.util.Map;
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

    private McpSseClient clientWithHeaders(ExchangeFunction exchange) {
        NsbhProperties.McpServerConfig cfg = new NsbhProperties.McpServerConfig();
        cfg.setName("test");
        cfg.setUrl("http://mcp-server/sse");
        cfg.setHeaders(Map.of("Authorization", "Bearer token"));
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

    @Test
    void callToolWithMultipleTextItemsConcatenatesThem() {
        String rpcResponse = """
                {"jsonrpc":"2.0","id":"x","result":{"content":[
                  {"type":"text","text":"line1"},
                  {"type":"text","text":"line2"}
                ]}}
                """.replace("\n", "");

        String result = client(sseResponse(rpcResponse))
                .callTool("my_tool", "{}").block();

        assertTrue(result.contains("line1"));
        assertTrue(result.contains("line2"));
    }

    @Test
    void callToolWithNonTextContentIgnoresItem() {
        String rpcResponse = """
                {"jsonrpc":"2.0","id":"x","result":{"content":[
                  {"type":"image","data":"base64"},
                  {"type":"text","text":"ok"}
                ]}}
                """.replace("\n", "");

        String result = client(sseResponse(rpcResponse))
                .callTool("my_tool", "{}").block();

        assertEquals("ok", result);
    }

    @Test
    void callToolWithInvalidJsonInputThrowsMcpException() {
        ExchangeFunction unused = req -> Mono.empty();
        assertThrows(McpException.class,
                () -> client(unused).callTool("tool", "not-json").block());
    }

    @Test
    void listToolsWithNoToolsKeyReturnsEmptyList() {
        String rpcResponse = "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":{}}";

        List<McpToolDefinition> tools = client(sseResponse(rpcResponse))
                .listTools().block();

        assertTrue(tools.isEmpty());
    }

    @Test
    void callToolWithNoContentKeyReturnsEmpty() {
        String rpcResponse = "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":{}}";

        String result = client(sseResponse(rpcResponse))
                .callTool("tool", "{}").block();

        assertEquals("", result);
    }

    @Test
    void closeReturnsMono() {
        McpSseClient c = client(req -> Mono.empty());
        c.close().block();
    }

    @Test
    void headersPropagatedToRequest() {
        String rpcResponse = "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":{}}";
        List<McpToolDefinition> tools = clientWithHeaders(sseResponse(rpcResponse))
                .listTools().block();
        assertTrue(tools.isEmpty());
    }

    @Test
    void serverErrorResponseThrowsMcpException() {
        ExchangeFunction errorResponse = req -> Mono.just(
                ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"error\":\"internal\"}")
                        .build());

        assertThrows(McpException.class,
                () -> client(errorResponse).listTools().block());
    }

    @Test
    void listToolsWithNonMapResultReturnsEmpty() {
        String rpcResponse = "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":\"not-a-map\"}";
        List<McpToolDefinition> tools = client(sseResponse(rpcResponse)).listTools().block();
        assertTrue(tools.isEmpty());
    }

    @Test
    void callToolWithNonMapResultReturnsEmpty() {
        String rpcResponse = "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":\"not-a-map\"}";
        String result = client(sseResponse(rpcResponse)).callTool("tool", "{}").block();
        assertEquals("", result);
    }

    @Test
    void listToolsIgnoresNonMapItemInToolsList() {
        String rpcResponse = "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":{\"tools\":[\"string-item\"]}}";
        List<McpToolDefinition> tools = client(sseResponse(rpcResponse)).listTools().block();
        assertTrue(tools.isEmpty());
    }

    @Test
    void listToolsWithMissingDescriptionDefaultsToEmpty() {
        String rpcResponse = "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":{\"tools\":[" +
                "{\"name\":\"t\",\"inputSchema\":{\"type\":\"object\"}}" +
                "]}}";
        List<McpToolDefinition> tools = client(sseResponse(rpcResponse)).listTools().block();
        assertEquals(1, tools.size());
        assertEquals("", tools.get(0).description());
    }

    @Test
    void callToolWithMissingTextFieldAppendsEmpty() {
        String rpcResponse = "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":{\"content\":[" +
                "{\"type\":\"text\"}" +
                "]}}";
        String result = client(sseResponse(rpcResponse)).callTool("tool", "{}").block();
        assertEquals("", result);
    }

    @Test
    void callToolWithNullInputJsonUsesEmptyArguments() {
        String rpcResponse = "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":{\"content\":[" +
                "{\"type\":\"text\",\"text\":\"ok\"}" +
                "]}}";
        String result = client(sseResponse(rpcResponse)).callTool("tool", null).block();
        assertEquals("ok", result);
    }

    @Test
    void filterIgnoresDoneAndNonJsonLines() {
        ExchangeFunction exchange = req -> {
            String body = "data: [DONE]\nevent: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":{}}\n\n";
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .body(body)
                    .build());
        };
        String result = client(exchange).callTool("tool", "{}").block();
        assertEquals("", result);
    }
}
