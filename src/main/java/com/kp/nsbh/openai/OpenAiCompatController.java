package com.kp.nsbh.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.agent.StatelessOrchestrator;
import com.kp.nsbh.config.NsbhProperties;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1")
public class OpenAiCompatController {

    private final StatelessOrchestrator orchestrator;
    private final NsbhProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiCompatController(StatelessOrchestrator orchestrator,
                                  NsbhProperties properties,
                                  ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/models")
    public Mono<Map<String, Object>> models() {
        String modelId = properties.getLlm().getModelDefault();
        Map<String, Object> model = Map.of("id", modelId, "object", "model", "owned_by", "nsbh");
        return Mono.just(Map.of("object", "list", "data", List.of(model)));
    }

    @PostMapping(value = "/chat/completions",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> chatCompletions(@RequestBody OpenAiChatRequest request) {
        if (request.messages() == null || request.messages().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(openAiError("messages cannot be empty", "invalid_request_error")));
        }
        String model = request.model() != null
                ? request.model()
                : properties.getLlm().getModelDefault();
        return orchestrator.chat(request.messages(), model)
                .map(text -> ResponseEntity.ok().<Object>body(buildChatResponse(text, model)))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .<Object>body(openAiError(
                                e.getMessage() != null ? e.getMessage() : "LLM error",
                                "server_error"))));
    }

    @PostMapping(value = "/chat/completions",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatCompletionsStream(@RequestBody OpenAiChatRequest request) {
        if (request.messages() == null || request.messages().isEmpty()) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .data(toJson(openAiError("messages cannot be empty", "invalid_request_error")))
                    .build());
        }
        String id = "chatcmpl-" + UUID.randomUUID();
        long created = Instant.now().getEpochSecond();
        String model = request.model() != null
                ? request.model()
                : properties.getLlm().getModelDefault();

        Flux<ServerSentEvent<String>> firstChunk = Flux.just(
                sseChunk(id, created, model, new OpenAiChatChunk.Delta("assistant", null), null));

        Flux<ServerSentEvent<String>> contentChunks = orchestrator.stream(request.messages(), model)
                .map(token -> sseChunk(id, created, model,
                        new OpenAiChatChunk.Delta(null, token), null));

        Flux<ServerSentEvent<String>> stopChunk = Flux.just(
                sseChunk(id, created, model, new OpenAiChatChunk.Delta(null, null), "stop"));

        Flux<ServerSentEvent<String>> done = Flux.just(
                ServerSentEvent.<String>builder().data("[DONE]").build());

        return firstChunk.concatWith(contentChunks).concatWith(stopChunk).concatWith(done)
                .onErrorResume(e -> Flux.just(ServerSentEvent.<String>builder()
                        .data(toJson(openAiError(
                                e.getMessage() != null ? e.getMessage() : "stream error",
                                "server_error")))
                        .build()));
    }

    private ServerSentEvent<String> sseChunk(String id, long created, String model,
                                              OpenAiChatChunk.Delta delta, String finishReason) {
        OpenAiChatChunk chunk = new OpenAiChatChunk(
                id, "chat.completion.chunk", created, model,
                List.of(new OpenAiChatChunk.ChunkChoice(0, delta, finishReason)));
        return ServerSentEvent.<String>builder().data(toJson(chunk)).build();
    }

    private OpenAiChatResponse buildChatResponse(String text, String model) {
        return new OpenAiChatResponse(
                "chatcmpl-" + UUID.randomUUID(),
                "chat.completion",
                Instant.now().getEpochSecond(),
                model,
                List.of(new OpenAiChatResponse.Choice(
                        0,
                        new OpenAiChatResponse.Message("assistant", text),
                        "stop")),
                new OpenAiChatResponse.Usage(0, 0, 0));
    }

    private Map<String, Object> openAiError(String message, String type) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("message", message);
        detail.put("type", type);
        detail.put("code", null);
        return Map.of("error", detail);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
