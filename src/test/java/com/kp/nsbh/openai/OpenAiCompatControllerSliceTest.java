package com.kp.nsbh.openai;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.kp.nsbh.agent.StatelessOrchestrator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest
@AutoConfigureWebTestClient
@TestPropertySource(properties = {"nsbh.api.key="})
class OpenAiCompatControllerSliceTest {

    @MockBean
    StatelessOrchestrator orchestrator;

    @Autowired
    WebTestClient webTestClient;

    @Test
    void nonStreamLlmErrorReturns502() {
        when(orchestrator.chat(anyList(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("LLM failed")));

        webTestClient.post().uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("server_error");
    }

    @Test
    void streamLlmErrorReturnsErrorEventInSse() {
        when(orchestrator.stream(anyList(), anyString()))
                .thenReturn(Flux.error(new RuntimeException("stream failed")));

        List<String> events = webTestClient.post().uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue("{\"model\":\"mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"stream\":true}")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .collectList()
                .block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> e.contains("server_error")));
    }

    @Test
    void nonStreamWithNullExceptionMessageUsesDefaultMessage() {
        when(orchestrator.chat(anyList(), anyString()))
                .thenReturn(Mono.error(new RuntimeException((String) null)));

        webTestClient.post().uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("server_error");
    }
}
