package com.kp.nsbh.api;

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

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
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
