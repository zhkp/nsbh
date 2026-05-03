package com.kp.nsbh.agent;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.memory.entity.MessageType;
import com.kp.nsbh.memory.repo.MessageRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class PromptBuilder {

    private final MessageRepository messageRepository;
    private final NsbhProperties properties;

    public PromptBuilder(MessageRepository messageRepository, NsbhProperties properties) {
        this.messageRepository = messageRepository;
        this.properties = properties;
    }

    public Mono<List<MessageEntity>> buildPromptWindow(UUID conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .collectList()
                .map(this::assemble);
    }

    private List<MessageEntity> assemble(List<MessageEntity> allMessages) {
        MessageEntity summary = null;
        List<MessageEntity> normals = new ArrayList<>();
        for (MessageEntity msg : allMessages) {
            if (msg.getType() == MessageType.SUMMARY) {
                summary = msg;
            } else if (msg.getType() == MessageType.NORMAL) {
                normals.add(msg);
            }
        }

        int window = Math.max(0, properties.getMemory().getWindow());
        List<MessageEntity> limited = normals.size() <= window
                ? normals
                : normals.subList(normals.size() - window, normals.size());

        List<MessageEntity> prompt = new ArrayList<>();
        prompt.add(systemPromptMessage());
        if (summary != null) {
            prompt.add(summary);
        }
        prompt.addAll(limited);
        return prompt;
    }

    private MessageEntity systemPromptMessage() {
        MessageEntity msg = new MessageEntity();
        msg.setRole(MessageRole.SYSTEM);
        msg.setType(MessageType.NORMAL);
        msg.setContent(properties.getMemory().getSystemPrompt());
        return msg;
    }
}
