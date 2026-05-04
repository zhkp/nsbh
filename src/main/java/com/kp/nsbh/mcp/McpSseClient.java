package com.kp.nsbh.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class McpSseClient implements McpClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final WebClient webClient;
    private final String serverUrl;
    private final ObjectMapper objectMapper;

    public McpSseClient(WebClient.Builder webClientBuilder,
                        NsbhProperties.McpServerConfig config,
                        ObjectMapper objectMapper) {
        WebClient.Builder b = webClientBuilder;
        if (config.getHeaders() != null) {
            for (Map.Entry<String, String> h : config.getHeaders().entrySet()) {
                b = b.defaultHeader(h.getKey(), h.getValue());
            }
        }
        this.webClient = b.build();
        this.serverUrl = config.getUrl();
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<List<McpToolDefinition>> listTools() {
        return sendRpc("tools/list", Map.of())
                .map(result -> {
                    if (!(result instanceof Map<?, ?> resultMap)) return List.of();
                    Object rawTools = resultMap.get("tools");
                    if (!(rawTools instanceof List<?> list)) return List.of();
                    List<McpToolDefinition> defs = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            String name = String.valueOf(m.get("name"));
                            String desc = m.get("description") != null ? String.valueOf(m.get("description")) : "";
                            String schema = toJson(m.get("inputSchema"));
                            defs.add(new McpToolDefinition(name, desc, schema));
                        }
                    }
                    return defs;
                });
    }

    @Override
    public Mono<String> callTool(String name, String inputJson) {
        Object arguments;
        try {
            arguments = objectMapper.readValue(inputJson == null ? "{}" : inputJson, MAP_TYPE);
        } catch (Exception e) {
            return Mono.error(new McpException("Invalid input JSON: " + e.getMessage()));
        }

        return sendRpc("tools/call", Map.of("name", name, "arguments", arguments))
                .map(result -> {
                    if (!(result instanceof Map<?, ?> resultMap)) return "";
                    Object rawContent = resultMap.get("content");
                    if (!(rawContent instanceof List<?> list)) return "";
                    StringBuilder sb = new StringBuilder();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m && "text".equals(m.get("type"))) {
                            if (!sb.isEmpty()) sb.append("\n");
                            sb.append(m.get("text") != null ? m.get("text") : "");
                        }
                    }
                    return sb.toString();
                });
    }

    @Override
    public Mono<Void> close() {
        return Mono.empty();
    }

    private Mono<Object> sendRpc(String method, Object params) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", method,
                "params", params
        );

        return webClient.post()
                .uri(serverUrl)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new McpException("MCP server error: " + body))))
                .bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMapMany(body -> Flux.fromArray(body.split("\n")))
                .map(String::trim)
                .map(line -> line.startsWith("data:") ? line.substring(5).trim() : line)
                .filter(json -> !json.isEmpty() && !"[DONE]".equals(json) && json.startsWith("{"))
                .flatMap(json -> {
                    try {
                        Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
                        if (parsed.containsKey("error")) {
                            return Flux.<Object>error(new McpException(
                                    "MCP error: " + parsed.get("error")));
                        }
                        return Flux.just(parsed.get("result"));
                    } catch (Exception e) {
                        return Flux.<Object>error(
                                new McpException("Failed to parse MCP response: " + e.getMessage()));
                    }
                })
                .next()
                .switchIfEmpty(Mono.error(
                        new McpException("MCP server returned empty response for " + method)));
    }

    private String toJson(Object obj) {
        if (obj == null) return "{}";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
