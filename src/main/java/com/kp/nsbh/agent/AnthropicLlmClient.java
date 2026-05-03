package com.kp.nsbh.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.tools.ToolRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(prefix = "nsbh.llm", name = "provider", havingValue = "anthropic")
public class AnthropicLlmClient implements LlmClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final int MAX_TOKENS = 4096;

    private final WebClient webClient;
    private final long timeoutMs;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolRegistry toolRegistry;

    public AnthropicLlmClient(WebClient.Builder webClientBuilder,
                               NsbhProperties properties,
                               ToolRegistry toolRegistry) {
        String apiKey = properties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is required when nsbh.llm.provider=anthropic");
        }
        this.webClient = webClientBuilder
                .baseUrl(properties.getLlm().getBaseUrl())
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
        this.timeoutMs = properties.getLlm().getTimeoutMs();
        this.toolRegistry = toolRegistry;
    }

    @Override
    public Mono<LlmReply> firstReply(String userMessage, String model,
                                      List<MessageEntity> memoryWindow) {
        AnthropicRequest request = buildRequest(model, memoryWindow);
        return call(request).map(response -> {
            List<AnthropicContentBlock> content = response.content();
            if (content == null || content.isEmpty()) {
                return LlmReply.text("");
            }
            List<ToolCallRequest> toolCalls = content.stream()
                    .filter(b -> "tool_use".equals(b.type()))
                    .map(b -> new ToolCallRequest(b.id(), b.name(),
                            b.input() != null ? toJson(b.input()) : "{}"))
                    .toList();
            if (!toolCalls.isEmpty()) {
                return LlmReply.withTools(toolCalls);
            }
            String text = content.stream()
                    .filter(b -> "text".equals(b.type()))
                    .map(AnthropicContentBlock::text)
                    .findFirst().orElse("");
            return LlmReply.text(text == null ? "" : text);
        });
    }

    @Override
    public Mono<String> finalReply(String userMessage, String model,
                                    String toolResult, List<MessageEntity> memoryWindow) {
        return firstReply(userMessage, model, memoryWindow)
                .map(r -> r.assistantMessage() == null ? "" : r.assistantMessage());
    }

    @Override
    public Flux<String> streamFirstReply(String userMessage, String model,
                                          List<MessageEntity> memoryWindow) {
        return firstReply(userMessage, model, memoryWindow)
                .flatMapMany(reply -> {
                    String msg = reply.assistantMessage();
                    if (msg == null || msg.isBlank()) return Flux.empty();
                    return Flux.just(msg);
                });
    }

    @Override
    public Mono<String> summarize(List<MessageEntity> messages, String model) {
        List<MessageEntity> withSystem = new ArrayList<>();
        MessageEntity sys = new MessageEntity();
        sys.setRole(MessageRole.SYSTEM);
        sys.setContent("Summarize this conversation briefly.");
        withSystem.add(sys);
        withSystem.addAll(messages);
        return firstReply("Summarize", model, withSystem)
                .map(r -> r.assistantMessage() == null ? "" : r.assistantMessage());
    }

    private AnthropicRequest buildRequest(String model, List<MessageEntity> window) {
        String systemText = window.stream()
                .filter(m -> m.getRole() == MessageRole.SYSTEM)
                .map(MessageEntity::getContent)
                .findFirst().orElse(null);

        List<AnthropicMessage> messages = window.stream()
                .filter(m -> m.getRole() != MessageRole.SYSTEM)
                .map(m -> new AnthropicMessage(mapRole(m.getRole()), m.getContent()))
                .toList();

        List<AnthropicTool> tools = toolRegistry.listMetadata().stream()
                .map(meta -> new AnthropicTool(meta.name(), meta.description(),
                        parseSchema(meta.schema())))
                .toList();

        return new AnthropicRequest(model, MAX_TOKENS, systemText, messages,
                tools.isEmpty() ? null : tools);
    }

    private Mono<AnthropicResponse> call(AnthropicRequest request) {
        return webClient.post()
                .uri("/v1/messages")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new LlmClientException(
                                "Anthropic error (" + resp.statusCode().value() + "): " + body))))
                .bodyToMono(AnthropicResponse.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .onErrorMap(LlmClientException.class, e -> e)
                .onErrorMap(e -> new LlmClientException("Anthropic request failed: " + e.getMessage()));
    }

    private String mapRole(MessageRole role) {
        return switch (role) {
            case ASSISTANT -> "assistant";
            default -> "user";
        };
    }

    private Map<String, Object> parseSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return Map.of("type", "object", "properties", Map.of());
        }
        try {
            return objectMapper.readValue(schemaJson, MAP_TYPE);
        } catch (Exception e) {
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    private String toJson(Map<String, Object> input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            return "{}";
        }
    }

    private record AnthropicRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            String system,
            List<AnthropicMessage> messages,
            List<AnthropicTool> tools
    ) {}

    private record AnthropicMessage(String role, String content) {}

    private record AnthropicTool(
            String name,
            String description,
            @JsonProperty("input_schema") Map<String, Object> inputSchema
    ) {}

    private record AnthropicResponse(
            List<AnthropicContentBlock> content,
            @JsonProperty("stop_reason") String stopReason
    ) {}

    private record AnthropicContentBlock(
            String type,
            String text,
            String id,
            String name,
            Map<String, Object> input
    ) {}
}
