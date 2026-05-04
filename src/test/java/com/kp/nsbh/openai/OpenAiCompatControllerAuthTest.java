package com.kp.nsbh.openai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
@TestPropertySource(properties = {"nsbh.api.key=test-secret"})
class OpenAiCompatControllerAuthTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void missingAuthHeaderReturns401() {
        webTestClient.get().uri("/v1/models")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("invalid_api_key");
    }

    @Test
    void invalidKeyReturns401() {
        webTestClient.get().uri("/v1/models")
                .header("Authorization", "Bearer wrong-key")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void validKeyAllowsAccess() {
        webTestClient.get().uri("/v1/models")
                .header("Authorization", "Bearer test-secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list");
    }

    @Test
    void validKeyAllowsChatCompletions() {
        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                .exchange()
                .expectStatus().isOk();
    }
}
