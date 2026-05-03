package com.kp.nsbh.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.memory.entity.MessageType;
import com.kp.nsbh.memory.repo.MessageRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class MemoryServiceTest {

    @Test
    void doesNotCompactWhenBelowThreshold() {
        MessageRepository repo = mock(MessageRepository.class);
        LlmClient llmClient = mock(LlmClient.class);
        NsbhProperties props = new NsbhProperties();
        props.getMemory().setCompactAfter(5);

        when(repo.findByConversationIdOrderByCreatedAtAsc(any(UUID.class)))
                .thenReturn(Flux.just(normal(), normal(), normal()));

        MemoryService service = new MemoryService(repo, llmClient, props);
        service.maybeCompact(UUID.randomUUID(), "model").block();

        verify(llmClient, never()).summarize(anyList(), anyString());
    }

    @Test
    void compactsWhenAboveThreshold() {
        MessageRepository repo = mock(MessageRepository.class);
        LlmClient llmClient = mock(LlmClient.class);
        NsbhProperties props = new NsbhProperties();
        props.getMemory().setCompactAfter(2);

        UUID convId = UUID.randomUUID();
        when(repo.findByConversationIdOrderByCreatedAtAsc(convId))
                .thenReturn(Flux.just(normal(), normal(), normal()));
        when(llmClient.summarize(anyList(), anyString())).thenReturn(Mono.just("summary text"));
        when(repo.save(any(MessageEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        MemoryService service = new MemoryService(repo, llmClient, props);
        service.maybeCompact(convId, "model").block();

        verify(llmClient).summarize(anyList(), anyString());
        verify(repo).save(any(MessageEntity.class));
    }

    @Test
    void deletesExistingSummaryBeforeWritingNew() {
        MessageRepository repo = mock(MessageRepository.class);
        LlmClient llmClient = mock(LlmClient.class);
        NsbhProperties props = new NsbhProperties();
        props.getMemory().setCompactAfter(1);

        UUID convId = UUID.randomUUID();
        UUID summaryId = UUID.randomUUID();
        MessageEntity existingSummary = new MessageEntity();
        existingSummary.setId(summaryId);
        existingSummary.setType(MessageType.SUMMARY);

        when(repo.findByConversationIdOrderByCreatedAtAsc(convId))
                .thenReturn(Flux.just(existingSummary, normal(), normal()));
        when(llmClient.summarize(anyList(), anyString())).thenReturn(Mono.just("new summary"));
        when(repo.deleteById(summaryId)).thenReturn(Mono.empty());
        when(repo.save(any(MessageEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        MemoryService service = new MemoryService(repo, llmClient, props);
        service.maybeCompact(convId, "model").block();

        verify(repo).deleteById(summaryId);
    }

    private static MessageEntity normal() {
        MessageEntity m = new MessageEntity();
        m.setRole(MessageRole.USER);
        m.setType(MessageType.NORMAL);
        m.setContent("msg");
        return m;
    }
}
