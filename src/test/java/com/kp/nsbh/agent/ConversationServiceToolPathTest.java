package com.kp.nsbh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.ConversationEntity;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.memory.entity.MessageType;
import com.kp.nsbh.memory.repo.ConversationRepository;
import com.kp.nsbh.memory.repo.MessageRepository;
import com.kp.nsbh.tools.ToolCallReason;
import com.kp.nsbh.tools.ToolCallStatus;
import com.kp.nsbh.tools.ToolExecutionResult;
import com.kp.nsbh.tools.ToolService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ConversationServiceToolPathTest {

    @Test
    void chatShouldExecuteToolAndReturnFinalAssistant() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        LlmClient llmClient = mock(LlmClient.class);
        ToolService toolService = mock(ToolService.class);

        NsbhProperties properties = new NsbhProperties();
        properties.getMemory().setCompactAfter(100);
        properties.getMemory().setWindow(10);

        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(UUID.randomUUID());
        when(conversationRepository.findById(conversation.getId())).thenReturn(Mono.just(conversation));
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()))
                .thenReturn(Flux.just(normal(MessageRole.USER, "u"), normal(MessageRole.ASSISTANT, "a")));
        when(llmClient.firstReply(anyString(), anyString(), any()))
                .thenReturn(Mono.just(new LlmReply(null, new ToolCallRequest("call-1", "time", "{}"))));
        when(toolService.execute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(new ToolExecutionResult("time", ToolCallStatus.SUCCESS, ToolCallReason.NONE, "2026-02-17T00:00:00Z", "call-1")));
        when(llmClient.finalReply(anyString(), anyString(), anyString(), any())).thenReturn(Mono.just("final-answer"));

        ConversationService service = new ConversationService(
                conversationRepository,
                messageRepository,
                properties,
                llmClient,
                toolService
        );

        ChatResult result = service.chat(conversation.getId(), "what time", null).block();

        assertEquals("final-answer", result.assistantMessage());
        assertEquals(1, result.toolCalls().size());
        assertEquals(ToolCallStatus.SUCCESS, result.toolCalls().getFirst().status());
    }

    @Test
    void chatShouldMapFirstReplyLlmErrorToBadGateway() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        LlmClient llmClient = mock(LlmClient.class);
        ToolService toolService = mock(ToolService.class);

        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(UUID.randomUUID());
        when(conversationRepository.findById(conversation.getId())).thenReturn(Mono.just(conversation));
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId())).thenReturn(Flux.empty());
        when(llmClient.firstReply(anyString(), anyString(), any())).thenReturn(Mono.error(new LlmClientException("upstream fail")));

        ConversationService service = new ConversationService(
                conversationRepository,
                messageRepository,
                new NsbhProperties(),
                llmClient,
                toolService
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.chat(conversation.getId(), "hello", null).block());
        assertEquals(502, ex.getStatusCode().value());
    }

    @Test
    void chatShouldMapFinalReplyLlmErrorToBadGateway() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        LlmClient llmClient = mock(LlmClient.class);
        ToolService toolService = mock(ToolService.class);

        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(UUID.randomUUID());
        when(conversationRepository.findById(conversation.getId())).thenReturn(Mono.just(conversation));
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId())).thenReturn(Flux.empty());
        when(llmClient.firstReply(anyString(), anyString(), any()))
                .thenReturn(Mono.just(new LlmReply(null, new ToolCallRequest("call-1", "time", "{}"))));
        when(toolService.execute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(new ToolExecutionResult("time", ToolCallStatus.SUCCESS, ToolCallReason.NONE, "ok", "call-1")));
        when(llmClient.finalReply(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.error(new LlmClientException("bad gateway")));

        ConversationService service = new ConversationService(
                conversationRepository,
                messageRepository,
                new NsbhProperties(),
                llmClient,
                toolService
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.chat(conversation.getId(), "hello", null).block());
        assertEquals(502, ex.getStatusCode().value());
    }

    @Test
    void chatShouldUseDefaultModelWhenInputModelBlank() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        LlmClient llmClient = mock(LlmClient.class);
        ToolService toolService = mock(ToolService.class);

        NsbhProperties properties = new NsbhProperties();
        properties.getLlm().setModelDefault("default-model");
        AtomicReference<String> modelSeen = new AtomicReference<>();

        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(UUID.randomUUID());
        when(conversationRepository.findById(conversation.getId())).thenReturn(Mono.just(conversation));
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId())).thenReturn(Flux.empty());
        when(llmClient.firstReply(anyString(), anyString(), any())).thenAnswer(inv -> {
            modelSeen.set(inv.getArgument(1));
            return Mono.just(new LlmReply("assistant", null));
        });

        ConversationService service = new ConversationService(
                conversationRepository,
                messageRepository,
                properties,
                llmClient,
                toolService
        );

        service.chat(conversation.getId(), "hello", "   ").block();
        assertEquals("default-model", modelSeen.get());
    }

    private static MessageEntity normal(MessageRole role, String content) {
        MessageEntity message = new MessageEntity();
        message.setRole(role);
        message.setType(MessageType.NORMAL);
        message.setContent(content);
        return message;
    }
}
