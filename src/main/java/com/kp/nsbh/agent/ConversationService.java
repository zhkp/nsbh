package com.kp.nsbh.agent;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.repo.ConversationRepository;
import com.kp.nsbh.memory.repo.MessageRepository;
import com.kp.nsbh.tools.ToolExecutionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ChatOrchestrator chatOrchestrator;
    private final NsbhProperties properties;

    public ConversationService(ConversationRepository conversationRepository,
                                MessageRepository messageRepository,
                                ChatOrchestrator chatOrchestrator,
                                NsbhProperties properties) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.chatOrchestrator = chatOrchestrator;
        this.properties = properties;
    }

    public Mono<com.kp.nsbh.memory.entity.ConversationEntity> createConversation() {
        return conversationRepository.save(new com.kp.nsbh.memory.entity.ConversationEntity());
    }

    public Mono<ChatResult> chat(UUID conversationId, String userMessage, String model) {
        String modelToUse = (model == null || model.isBlank())
                ? properties.getLlm().getModelDefault() : model;
        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found")))
                .flatMap(ignored -> chatOrchestrator.orchestrate(conversationId, userMessage, modelToUse)
                        .collectList()
                        .map(this::toChatResult));
    }

    public Flux<MessageEntity> getMessages(UUID conversationId) {
        return conversationRepository.existsById(conversationId)
                .flatMapMany(exists -> {
                    if (!exists) {
                        return Flux.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Conversation not found"));
                    }
                    return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
                });
    }

    private ChatResult toChatResult(List<AgentEvent> events) {
        String fullText = "";
        List<ToolExecutionResult> toolResults = new ArrayList<>();
        for (AgentEvent event : events) {
            if (event instanceof AgentEvent.Done done) {
                fullText = done.fullText();
                toolResults = new ArrayList<>(done.toolResults());
            }
        }
        return new ChatResult(fullText, toolResults);
    }
}
