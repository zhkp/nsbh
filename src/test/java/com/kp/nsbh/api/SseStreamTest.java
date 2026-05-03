package com.kp.nsbh.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.kp.nsbh.agent.AgentEvent;
import com.kp.nsbh.agent.ChatOrchestrator;
import com.kp.nsbh.agent.ConversationService;
import com.kp.nsbh.memory.entity.ConversationEntity;
import com.kp.nsbh.memory.repo.ConversationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(ConversationController.class)
class SseStreamTest {

    @Autowired
    WebTestClient webTestClient;

    @MockBean
    ConversationService conversationService;

    @MockBean
    ChatOrchestrator chatOrchestrator;

    @MockBean
    ConversationRepository conversationRepository;

    @Test
    void streamEndpointReturnsTextEventStream() {
        UUID convId = UUID.randomUUID();
        ConversationEntity conv = new ConversationEntity();
        conv.setId(convId);

        when(conversationRepository.findById(convId)).thenReturn(Mono.just(conv));
        when(chatOrchestrator.orchestrate(any(), anyString(), anyString()))
                .thenReturn(Flux.just(
                        new AgentEvent.TextDelta("hello"),
                        new AgentEvent.Done("hello", List.of())
                ));

        String response = webTestClient.post()
                .uri("/api/v1/conversations/" + convId + "/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":\"hi\"}")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class)
                .getResponseBody()
                .collectList()
                .block()
                .stream()
                .reduce("", String::concat);

        assertTrue(response.contains("text_delta") || response.contains("hello"));
        assertFalse(response.isEmpty());
    }
}
