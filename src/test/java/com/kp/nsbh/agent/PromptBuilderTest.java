package com.kp.nsbh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.memory.entity.MessageType;
import com.kp.nsbh.memory.repo.MessageRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class PromptBuilderTest {

    @Test
    void windowLimitsToLatestNNormals() {
        MessageRepository repo = mock(MessageRepository.class);
        NsbhProperties props = new NsbhProperties();
        props.getMemory().setWindow(2);
        props.getMemory().setSystemPrompt("sys");

        MessageEntity m1 = normal("first");
        MessageEntity m2 = normal("second");
        MessageEntity m3 = normal("third");
        when(repo.findByConversationIdOrderByCreatedAtAsc(any(UUID.class)))
                .thenReturn(Flux.just(m1, m2, m3));

        PromptBuilder builder = new PromptBuilder(repo, props);
        List<MessageEntity> result = builder.buildPromptWindow(UUID.randomUUID()).block();

        // system prompt + last 2 normals
        assertEquals(3, result.size());
        assertEquals(MessageRole.SYSTEM, result.get(0).getRole());
        assertEquals("second", result.get(1).getContent());
        assertEquals("third", result.get(2).getContent());
    }

    @Test
    void negativeWindowReturnsOnlySystemPrompt() {
        MessageRepository repo = mock(MessageRepository.class);
        NsbhProperties props = new NsbhProperties();
        props.getMemory().setWindow(-1);
        props.getMemory().setSystemPrompt("sys");

        when(repo.findByConversationIdOrderByCreatedAtAsc(any(UUID.class)))
                .thenReturn(Flux.just(normal("a"), normal("b")));

        PromptBuilder builder = new PromptBuilder(repo, props);
        List<MessageEntity> result = builder.buildPromptWindow(UUID.randomUUID()).block();

        assertEquals(1, result.size());
        assertEquals(MessageRole.SYSTEM, result.get(0).getRole());
    }

    @Test
    void summaryInsertedAfterSystemPrompt() {
        MessageRepository repo = mock(MessageRepository.class);
        NsbhProperties props = new NsbhProperties();
        props.getMemory().setWindow(10);
        props.getMemory().setSystemPrompt("sys");

        MessageEntity summary = summary("sum");
        MessageEntity normal = normal("msg");
        when(repo.findByConversationIdOrderByCreatedAtAsc(any(UUID.class)))
                .thenReturn(Flux.just(summary, normal));

        PromptBuilder builder = new PromptBuilder(repo, props);
        List<MessageEntity> result = builder.buildPromptWindow(UUID.randomUUID()).block();

        assertEquals(3, result.size());
        assertEquals(MessageRole.SYSTEM, result.get(0).getRole());
        assertEquals(MessageType.SUMMARY, result.get(1).getType());
        assertEquals("msg", result.get(2).getContent());
    }

    private static MessageEntity normal(String content) {
        MessageEntity m = new MessageEntity();
        m.setRole(MessageRole.USER);
        m.setType(MessageType.NORMAL);
        m.setContent(content);
        return m;
    }

    private static MessageEntity summary(String content) {
        MessageEntity m = new MessageEntity();
        m.setRole(MessageRole.SYSTEM);
        m.setType(MessageType.SUMMARY);
        m.setContent(content);
        return m;
    }
}
