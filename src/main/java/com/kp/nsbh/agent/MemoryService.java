package com.kp.nsbh.agent;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.memory.entity.MessageType;
import com.kp.nsbh.memory.repo.MessageRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class MemoryService {

    private final MessageRepository messageRepository;
    private final LlmClient llmClient;
    private final NsbhProperties properties;

    public MemoryService(MessageRepository messageRepository,
                         LlmClient llmClient,
                         NsbhProperties properties) {
        this.messageRepository = messageRepository;
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public Mono<Void> maybeCompact(UUID conversationId, String model) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .collectList()
                .flatMap(allMessages -> {
                    List<MessageEntity> normals = allMessages.stream()
                            .filter(m -> m.getType() == MessageType.NORMAL)
                            .toList();
                    if (normals.size() <= properties.getMemory().getCompactAfter()) {
                        return Mono.empty();
                    }
                    List<MessageEntity> summaries = allMessages.stream()
                            .filter(m -> m.getType() == MessageType.SUMMARY)
                            .toList();
                    return llmClient.summarize(normals, model)
                            .flatMap(summaryText ->
                                    Flux.fromIterable(summaries)
                                            .flatMap(s -> messageRepository.deleteById(s.getId()))
                                            .then()
                                            .then(messageRepository.save(
                                                    newSummary(conversationId, summaryText)))
                                            .then()
                            );
                })
                .then();
    }

    private MessageEntity newSummary(UUID conversationId, String content) {
        MessageEntity msg = new MessageEntity();
        msg.setConversationId(conversationId);
        msg.setRole(MessageRole.SYSTEM);
        msg.setType(MessageType.SUMMARY);
        msg.setContent(content == null ? "" : content);
        return msg;
    }
}
