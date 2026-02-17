package com.kp.nsbh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.repo.ConversationRepository;
import com.kp.nsbh.memory.repo.MessageRepository;
import com.kp.nsbh.tools.ToolService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

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
}
