package com.kp.nsbh.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.tools.ToolMetadata;
import com.kp.nsbh.tools.ToolRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(prefix = "nsbh.llm", name = "provider", havingValue = "openai")
public class OpenAiLlmClient implements LlmClient {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final WebClient webClient;
    private final long timeoutMs;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;

    public OpenAiLlmClient(WebClient.Builder webClientBuilder,
                           NsbhProperties properties,
                           ObjectMapper objectMapper,
                           ToolRegistry toolRegistry) {
        String apiKey = properties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required when nsbh.llm.provider=openai");
        }
        this.webClient = webClientBuilder
                .baseUrl(properties.getLlm().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
        this.timeoutMs = properties.getLlm().getTimeoutMs();
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public LlmReply firstReply(String userMessage, String model, List<MessageEntity> memoryWindow) {
        ChatCompletionsRequest request = new ChatCompletionsRequest(
                model,
                toMessages(memoryWindow),
                buildToolDefinitions(),
                "auto"
        );
        ChatCompletionsResponse response = callOpenAi(request);
        ChatCompletionsMessage message = firstMessage(response);

        List<ChatCompletionsToolCall> toolCalls = message.toolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            ChatCompletionsToolCall call = toolCalls.get(0);
            if (call.function() == null || call.function().name() == null || call.function().name().isBlank()) {
                throw new LlmClientException("OpenAI returned invalid tool call");
            }
            String args = call.function() != null && call.function().arguments() != null
                    ? call.function().arguments()
                    : "{}";
            return new LlmReply(null, new ToolCallRequest(call.id(), call.function().name(), args));
        }

        return new LlmReply(message.content() == null ? "" : message.content(), null);
    }

    @Override
    public String finalReply(String userMessage, String model, String toolResult, List<MessageEntity> memoryWindow) {
        ChatCompletionsRequest request = new ChatCompletionsRequest(
                model,
                toMessages(memoryWindow),
                null,
                null
        );
        ChatCompletionsResponse response = callOpenAi(request);
        ChatCompletionsMessage message = firstMessage(response);
        return message.content() == null ? "" : message.content();
    }

    @Override
    public String summarize(List<MessageEntity> messages, String model) {
        List<ChatCompletionsInputMessage> prompt = new ArrayList<>();
        prompt.add(new ChatCompletionsInputMessage(
                "system",
                "Summarize the conversation briefly for future context. Keep key facts and user intent.",
                null,
                null
        ));
        prompt.addAll(toMessages(messages));
        ChatCompletionsRequest request = new ChatCompletionsRequest(model, prompt, null, null);
        ChatCompletionsResponse response = callOpenAi(request);
        ChatCompletionsMessage message = firstMessage(response);
        return message.content() == null ? "" : message.content();
    }

    private ChatCompletionsResponse callOpenAi(ChatCompletionsRequest request) {
        try {
            return webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> Mono.error(mapError(clientResponse.statusCode(), body))))
                    .bodyToMono(ChatCompletionsResponse.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
        } catch (WebClientResponseException e) {
            throw mapError(e.getStatusCode(), e.getResponseBodyAsString());
        } catch (LlmClientException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmClientException("OpenAI request failed: " + rootCauseMessage(e), e);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        String compact = message.replace('\n', ' ').replace('\r', ' ');
        if (compact.length() > 240) {
            compact = compact.substring(0, 240) + "...";
        }
        return root.getClass().getSimpleName() + ": " + compact;
    }

    private LlmClientException mapError(HttpStatusCode statusCode, String body) {
        String errorMessage = extractOpenAiErrorMessage(body);
        String statusText = String.valueOf(statusCode.value());
        if (statusCode.value() == 401) {
            return new LlmClientException("OpenAI authentication failed (401)");
        }
        if (statusCode.value() == 429) {
            return new LlmClientException("OpenAI rate limit exceeded (429)");
        }
        if (statusCode.is5xxServerError()) {
            return new LlmClientException("OpenAI service unavailable (" + statusText + ")");
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            return new LlmClientException("OpenAI request failed (" + statusText + "): " + errorMessage);
        }
        return new LlmClientException("OpenAI request failed (" + statusText + ")");
    }

    private String extractOpenAiErrorMessage(String body) {
        if (body == null) {
            return null;
        }
        int idx = body.indexOf("\"message\"");
        if (idx < 0) {
            return null;
        }
        int start = body.indexOf('"', idx + 9);
        int end = start > -1 ? body.indexOf('"', start + 1) : -1;
        if (start < 0 || end <= start) {
            return null;
        }
        return body.substring(start + 1, end);
    }

    private ChatCompletionsMessage firstMessage(ChatCompletionsResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmClientException("OpenAI response has no choices");
        }
        ChatCompletionsMessage message = response.choices().get(0).message();
        if (message == null) {
            throw new LlmClientException("OpenAI response has no message");
        }
        return message;
    }

    private List<ChatCompletionsInputMessage> toMessages(List<MessageEntity> memoryWindow) {
        List<ChatCompletionsInputMessage> messages = new ArrayList<>();
        for (MessageEntity message : memoryWindow) {
            String role = mapRole(message.getRole());
            String toolCallId = message.getRole() == MessageRole.TOOL ? message.getToolCallId() : null;
            String name = message.getRole() == MessageRole.TOOL ? message.getToolName() : null;
            messages.add(new ChatCompletionsInputMessage(role, message.getContent(), toolCallId, name));
        }
        return messages;
    }

    private List<ChatCompletionsToolDefinition> buildToolDefinitions() {
        List<ChatCompletionsToolDefinition> tools = new ArrayList<>();
        for (ToolMetadata metadata : toolRegistry.listMetadata()) {
            tools.add(new ChatCompletionsToolDefinition(
                    "function",
                    new ChatCompletionsToolFunction(
                            metadata.name(),
                            metadata.description(),
                            parseSchema(metadata.schema())
                    )
            ));
        }
        return tools;
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

    private String mapRole(MessageRole role) {
        if (role == MessageRole.ASSISTANT) {
            return "assistant";
        }
        if (role == MessageRole.SYSTEM) {
            return "system";
        }
        if (role == MessageRole.TOOL) {
            return "tool";
        }
        return "user";
    }

    private record ChatCompletionsRequest(
            String model,
            List<ChatCompletionsInputMessage> messages,
            @JsonProperty("tools") List<ChatCompletionsToolDefinition> tools,
            @JsonProperty("tool_choice") String toolChoice
    ) {
    }

    private record ChatCompletionsInputMessage(
            String role,
            String content,
            @JsonProperty("tool_call_id") String toolCallId,
            String name
    ) {
    }

    private record ChatCompletionsToolDefinition(
            String type,
            ChatCompletionsToolFunction function
    ) {
    }

    private record ChatCompletionsToolFunction(
            String name,
            String description,
            Map<String, Object> parameters
    ) {
    }

    private record ChatCompletionsResponse(
            List<ChatCompletionsChoice> choices
    ) {
    }

    private record ChatCompletionsChoice(
            ChatCompletionsMessage message
    ) {
    }

    private record ChatCompletionsMessage(
            String role,
            String content,
            @JsonProperty("tool_calls") List<ChatCompletionsToolCall> toolCalls
    ) {
    }

    private record ChatCompletionsToolCall(
            String id,
            String type,
            ChatCompletionsToolCallFunction function
    ) {
    }

    private record ChatCompletionsToolCallFunction(
            String name,
            String arguments
    ) {
    }
}
