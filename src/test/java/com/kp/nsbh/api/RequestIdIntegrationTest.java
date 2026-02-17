package com.kp.nsbh.api;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class RequestIdIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void successResponseShouldContainRequestIdHeaderAndBody() {
        webTestClient.post()
                .uri("/api/v1/conversations")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.requestId").isNotEmpty();
    }

    @Test
    void errorResponseShouldContainRequestIdHeaderAndBody() {
        webTestClient.get()
                .uri("/api/v1/conversations/{id}/messages", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.requestId").isNotEmpty();
    }
}
