package com.kp.nsbh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.ConversationEntity;
import com.kp.nsbh.memory.repo.ConversationRepository;
import com.kp.nsbh.memory.repo.MessageRepository;
import com.kp.nsbh.tools.ToolService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ResponseStatusException;

class ConversationServicePromptWindowTest {

    @Test
    void shouldKeepOnlyLatestMessagesInWindow() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        LlmClient llmClient = mock(LlmClient.class);
        ToolService toolService = mock(ToolService.class);

        NsbhProperties properties = new NsbhProperties();
        properties.getMemory().setWindow(2);

        List<MessageEntity> messages = new ArrayList<>();
        messages.add(new MessageEntity());
        messages.add(new MessageEntity());
        messages.add(new MessageEntity());

        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any(UUID.class))).thenReturn(Flux.fromIterable(messages));

        ConversationService service = new ConversationService(
                conversationRepository,
                messageRepository,
                properties,
                llmClient,
                toolService
        );

        List<MessageEntity> result = service.getPromptWindow(UUID.randomUUID()).block();
        assertEquals(2, result.size());
        assertEquals(messages.get(1), result.get(0));
        assertEquals(messages.get(2), result.get(1));
    }

    @Test
    void chatShouldThrowNotFoundWhenConversationMissing() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        LlmClient llmClient = mock(LlmClient.class);
        ToolService toolService = mock(ToolService.class);

        when(conversationRepository.findById(any(UUID.class))).thenReturn(Mono.empty());

        ConversationService service = new ConversationService(
                conversationRepository,
                messageRepository,
                new NsbhProperties(),
                llmClient,
                toolService
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.chat(UUID.randomUUID(), "hello", null).block());
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getMessagesShouldThrowNotFoundWhenConversationMissing() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        LlmClient llmClient = mock(LlmClient.class);
        ToolService toolService = mock(ToolService.class);

        when(conversationRepository.existsById(any(UUID.class))).thenReturn(Mono.just(false));

        ConversationService service = new ConversationService(
                conversationRepository,
                messageRepository,
                new NsbhProperties(),
                llmClient,
                toolService
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getMessages(UUID.randomUUID()).collectList().block());
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void chatShouldSaveAssistantWhenNoToolCall() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        LlmClient llmClient = mock(LlmClient.class);
        ToolService toolService = mock(ToolService.class);

        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(UUID.randomUUID());
        when(conversationRepository.findById(any(UUID.class))).thenReturn(Mono.just(conversation));
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any(UUID.class))).thenReturn(Flux.empty());
        when(llmClient.firstReply(any(), any(), any())).thenReturn(Mono.just(new LlmReply("assistant", null)));

        ConversationService service = new ConversationService(
                conversationRepository,
                messageRepository,
                new NsbhProperties(),
                llmClient,
                toolService
        );

        ChatResult result = service.chat(conversation.getId(), "hello", null).block();
        assertEquals("assistant", result.assistantMessage());
        assertEquals(0, result.toolCalls().size());
    }
}
