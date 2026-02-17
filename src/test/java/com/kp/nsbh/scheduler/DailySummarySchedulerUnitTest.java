package com.kp.nsbh.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kp.nsbh.agent.LlmClient;
import com.kp.nsbh.agent.LlmClientException;
import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.ConversationEntity;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.memory.entity.MessageType;
import com.kp.nsbh.memory.repo.ConversationRepository;
import com.kp.nsbh.memory.repo.MessageRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DailySummarySchedulerUnitTest {

    @Test
    void runDailySummaryShouldSkipWhenConversationHasNoMessages() {
        MessageRepository messageRepository = org.mockito.Mockito.mock(MessageRepository.class);
        ConversationRepository conversationRepository = org.mockito.Mockito.mock(ConversationRepository.class);
        LlmClient llmClient = org.mockito.Mockito.mock(LlmClient.class);

        UUID conversationId = UUID.randomUUID();
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(conversationId);

        when(messageRepository.findConversationIdsWithMessagesSince(any(Instant.class)))
                .thenReturn(Flux.just(conversationId));
        when(conversationRepository.findById(conversationId)).thenReturn(Mono.just(conversation));
        when(messageRepository.findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(any(UUID.class), any(Instant.class)))
                .thenReturn(Flux.empty());

        DailySummaryScheduler scheduler = new DailySummaryScheduler(
                messageRepository,
                conversationRepository,
                llmClient,
                new NsbhProperties()
        );

        scheduler.runDailySummary().block();
        verify(messageRepository, never()).save(any(MessageEntity.class));
    }

    @Test
    void runDailySummaryShouldSwallowLlmClientException() {
        MessageRepository messageRepository = org.mockito.Mockito.mock(MessageRepository.class);
        ConversationRepository conversationRepository = org.mockito.Mockito.mock(ConversationRepository.class);
        LlmClient llmClient = org.mockito.Mockito.mock(LlmClient.class);

        NsbhProperties properties = new NsbhProperties();
        properties.getLlm().setModelDefault("test-model");

        UUID conversationId = UUID.randomUUID();
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(conversationId);
        MessageEntity message = new MessageEntity();
        message.setConversationId(conversationId);
        message.setRole(MessageRole.USER);
        message.setType(MessageType.NORMAL);
        message.setContent("hello");

        when(messageRepository.findConversationIdsWithMessagesSince(any(Instant.class)))
                .thenReturn(Flux.just(conversationId));
        when(conversationRepository.findById(conversationId)).thenReturn(Mono.just(conversation));
        when(messageRepository.findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(any(UUID.class), any(Instant.class)))
                .thenReturn(Flux.just(message));
        when(llmClient.summarize(any(), anyString())).thenReturn(Mono.error(new LlmClientException("llm down")));

        DailySummaryScheduler scheduler = new DailySummaryScheduler(
                messageRepository,
                conversationRepository,
                llmClient,
                properties
        );

        scheduler.runDailySummary().block();
        verify(messageRepository, never()).save(any(MessageEntity.class));
    }
}
