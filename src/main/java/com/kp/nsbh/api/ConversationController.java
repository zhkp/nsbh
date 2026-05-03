package com.kp.nsbh.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.agent.AgentEvent;
import com.kp.nsbh.agent.ChatOrchestrator;
import com.kp.nsbh.agent.ChatResult;
import com.kp.nsbh.agent.ConversationService;
import com.kp.nsbh.api.dto.ChatRequest;
import com.kp.nsbh.api.dto.ChatResponse;
import com.kp.nsbh.api.dto.CreateConversationResponse;
import com.kp.nsbh.api.dto.MessageDto;
import com.kp.nsbh.api.dto.ToolCallResultDto;
import com.kp.nsbh.memory.entity.MessageEntity;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    private final ConversationService conversationService;
    private final ChatOrchestrator chatOrchestrator;
    private final ObjectMapper objectMapper;

    public ConversationController(ConversationService conversationService,
                                   ChatOrchestrator chatOrchestrator,
                                   ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.chatOrchestrator = chatOrchestrator;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public Mono<CreateConversationResponse> createConversation(ServerWebExchange exchange) {
        String requestId = RequestIdSupport.currentRequestId(exchange);
        return conversationService.createConversation()
                .map(conversation -> new CreateConversationResponse(conversation.getId(), requestId));
    }

    @PostMapping("/{id}/chat")
    public Mono<ChatResponse> chat(@PathVariable("id") UUID id,
                                   @Valid @RequestBody ChatRequest request,
                                   ServerWebExchange exchange) {
        String requestId = RequestIdSupport.currentRequestId(exchange);
        return conversationService.chat(id, request.message(), request.model())
                .map(result -> {
                    List<ToolCallResultDto> toolCalls = result.toolCalls().stream()
                            .map(call -> new ToolCallResultDto(
                                    call.toolName(),
                                    call.status().name(),
                                    call.reason().name(),
                                    call.result()
                            ))
                            .toList();
                    return new ChatResponse(id, result.assistantMessage(), toolCalls, requestId);
                });
    }

    @PostMapping(value = "/{id}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@PathVariable("id") UUID id,
                                                    @Valid @RequestBody ChatRequest request) {
        String model = request.model();
        return chatOrchestrator.orchestrate(id, request.message(), model == null ? "" : model)
                .map(event -> ServerSentEvent.<String>builder()
                        .event(eventName(event))
                        .data(toJson(event))
                        .build());
    }

    private String eventName(AgentEvent event) {
        return switch (event) {
            case AgentEvent.TextDelta ignored -> "text_delta";
            case AgentEvent.ToolStart ignored -> "tool_start";
            case AgentEvent.ToolEnd ignored -> "tool_end";
            case AgentEvent.Done ignored -> "done";
        };
    }

    private String toJson(AgentEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            return "{}";
        }
    }

    @GetMapping("/{id}/messages")
    public Flux<MessageDto> messages(@PathVariable("id") UUID id) {
        return conversationService.getMessages(id).map(this::toDto);
    }

    private MessageDto toDto(MessageEntity message) {
        return new MessageDto(
                message.getId(),
                message.getRole().name(),
                message.getType().name(),
                message.getContent(),
                message.getToolName(),
                message.getToolCallId(),
                message.getCreatedAt()
        );
    }
}
