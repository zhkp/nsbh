package com.kp.nsbh.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "nsbh.tools.allowed[0]=dummy"
})
class ToolRejectionIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldMapNotAllowedReason() throws Exception {
        byte[] createBytes = webTestClient.post()
                .uri("/api/v1/conversations")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBodyContent();
        String createBody = createBytes == null ? "{}" : new String(createBytes, StandardCharsets.UTF_8);
        JsonNode createJson = objectMapper.readTree(createBody);
        String conversationId = createJson.get("conversationId").asText();

        webTestClient.post()
                .uri("/api/v1/conversations/{id}/chat", conversationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":\"what time\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.toolCalls[0].status").isEqualTo("REJECTED")
                .jsonPath("$.toolCalls[0].reason").isEqualTo("NOT_ALLOWED");
    }
}
