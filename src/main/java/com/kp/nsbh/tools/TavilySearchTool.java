package com.kp.nsbh.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@NsbhTool(
        name = "web_search",
        description = "Search the web using Tavily",
        schema = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"max_results\":{\"type\":\"integer\"}},\"required\":[\"query\"]}",
        requiredPermissions = {"NET_HTTP"}
)
public class TavilySearchTool implements Tool {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final NsbhProperties properties;

    public TavilySearchTool(WebClient.Builder webClientBuilder,
                             NsbhProperties properties,
                             ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.tavily.com")
                .build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Mono<String> execute(String inputJson) {
        String apiKey = properties.getTools().getWebSearch().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new IllegalArgumentException("TAVILY_API_KEY is not configured"));
        }
        return Mono.fromCallable(() -> {
            JsonNode root = objectMapper.readTree(inputJson == null ? "{}" : inputJson);
            String query = root.path("query").asText("").trim();
            if (query.isBlank()) {
                throw new IllegalArgumentException("query is required");
            }
            int maxResults = root.path("max_results").asInt(
                    properties.getTools().getWebSearch().getMaxResults());
            return Map.of("api_key", apiKey, "query", query, "max_results", maxResults);
        }).flatMap(requestBody ->
                webClient.post()
                        .uri("/search")
                        .bodyValue(requestBody)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(
                                        new IllegalStateException("Tavily error: " + body))))
                        .bodyToMono(String.class)
                        .timeout(Duration.ofMillis(properties.getTools().getTimeoutMs()))
                        .map(this::mapResponse)
        );
    }

    private static String orEmpty(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String mapResponse(String responseJson) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(responseJson, MAP_TYPE);
            Object rawResults = parsed.get("results");
            if (!(rawResults instanceof List<?> list)) {
                return "[]";
            }
            List<Map<String, String>> results = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("title", orEmpty(m.get("title")));
                    entry.put("url", orEmpty(m.get("url")));
                    entry.put("snippet", orEmpty(m.get("content")));
                    results.add(entry);
                }
            }
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            return "[]";
        }
    }
}
