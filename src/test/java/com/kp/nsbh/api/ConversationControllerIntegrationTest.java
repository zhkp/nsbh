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
        "nsbh.tools.allowed[0]=time"
})
class ConversationControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createChatAndReadMessagesShouldWork() throws Exception {
        byte[] createBytes = webTestClient.post()
                .uri("/api/v1/conversations")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.conversationId").isNotEmpty()
                .returnResult()
                .getResponseBodyContent();

        String createBody = createBytes == null ? "{}" : new String(createBytes, StandardCharsets.UTF_8);
        JsonNode createJson = objectMapper.readTree(createBody);
        String conversationId = createJson.get("conversationId").asText();

        webTestClient.post()
                .uri("/api/v1/conversations/{id}/chat", conversationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":\"请告诉我当前时间\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.assistantMessage").isNotEmpty()
                .jsonPath("$.toolCalls[0].toolName").isEqualTo("time")
                .jsonPath("$.toolCalls[0].status").isEqualTo("SUCCESS")
                .jsonPath("$.toolCalls[0].reason").isEqualTo("NONE");

        webTestClient.get()
                .uri("/api/v1/conversations/{id}/messages", conversationId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].role").isEqualTo("USER")
                .jsonPath("$[1].role").isEqualTo("TOOL")
                .jsonPath("$[2].role").isEqualTo("ASSISTANT");
    }
}
