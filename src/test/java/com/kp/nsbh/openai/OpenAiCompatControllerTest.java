package com.kp.nsbh.openai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
@TestPropertySource(properties = {"nsbh.api.key="})
class OpenAiCompatControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void modelsEndpointReturnsModelList() {
        webTestClient.get().uri("/v1/models")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data[0].id").isNotEmpty()
                .jsonPath("$.data[0].object").isEqualTo("model")
                .jsonPath("$.data[0].owned_by").isEqualTo("nsbh");
    }

    @Test
    void chatCompletionsNonStreamReturnsValidStructure() {
        webTestClient.post().uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"stream\":false}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("chat.completion")
                .jsonPath("$.choices[0].message.role").isEqualTo("assistant")
                .jsonPath("$.choices[0].message.content").isNotEmpty()
                .jsonPath("$.choices[0].finish_reason").isEqualTo("stop")
                .jsonPath("$.usage.prompt_tokens").isEqualTo(0);
    }

    @Test
    void chatCompletionsNonStreamWithNullModelUsesDefault() {
        webTestClient.post().uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.model").isNotEmpty();
    }

    @Test
    void chatCompletionsEmptyMessagesReturns400() {
        webTestClient.post().uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"mock\",\"messages\":[]}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error");
    }

    @Test
    void chatCompletionsStreamReturnsSseWithDone() {
        List<String> events = webTestClient.post().uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue("{\"model\":\"mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"stream\":true}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class)
                .getResponseBody()
                .collectList()
                .block();

        assertNotNull(events);
        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(e -> e.contains("[DONE]")));
        assertTrue(events.stream().anyMatch(e -> e.contains("chat.completion.chunk")));
    }

    @Test
    void chatCompletionsStreamEmptyMessagesReturnsErrorEvent() {
        List<String> events = webTestClient.post().uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue("{\"model\":\"mock\",\"messages\":[]}")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .collectList()
                .block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> e.contains("invalid_request_error")));
    }
}
