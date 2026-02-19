package com.kp.nsbh.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class ToolsControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void listToolsShouldReturnTimeMetadata() {
        webTestClient.get()
                .uri("/api/v1/tools")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    org.assertj.core.api.Assertions.assertThat(body).contains("\"name\":\"time\"");
                    org.assertj.core.api.Assertions.assertThat(body).contains("\"name\":\"http_get\"");
                    org.assertj.core.api.Assertions.assertThat(body).contains("\"requiredPermissions\":[\"NET_HTTP\"]");
                });
    }
}
