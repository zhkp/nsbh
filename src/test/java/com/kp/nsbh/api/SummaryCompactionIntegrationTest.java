package com.kp.nsbh.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        "nsbh.llm.provider=mock",
        "nsbh.memory.compactAfter=2",
        "nsbh.memory.window=2"
})
class SummaryCompactionIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCompactAndPersistSummaryMessage() throws Exception {
        byte[] createBytes = webTestClient.post()
                .uri("/api/v1/conversations")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBodyContent();
        String createBody = createBytes == null ? "{}" : new String(createBytes, StandardCharsets.UTF_8);
        String conversationId = objectMapper.readTree(createBody)
                .get("conversationId")
                .asText();

        sendChat(conversationId, "hello-1");
        sendChat(conversationId, "hello-2");
        sendChat(conversationId, "hello-3");

        byte[] messagesBytes = webTestClient.get()
                .uri("/api/v1/conversations/{id}/messages", conversationId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBodyContent();

        String messagesBody = messagesBytes == null ? "[]" : new String(messagesBytes, StandardCharsets.UTF_8);
        JsonNode messages = objectMapper.readTree(messagesBody);

        int summaryCount = 0;
        String summaryContent = "";
        for (JsonNode message : messages) {
            if ("SUMMARY".equals(message.get("type").asText())) {
                summaryCount++;
                summaryContent = message.get("content").asText();
                assertEquals("SYSTEM", message.get("role").asText());
            }
        }

        assertEquals(1, summaryCount);
        assertTrue(summaryContent.contains("SUMMARY messages=5"));
    }

    private void sendChat(String conversationId, String message) {
        webTestClient.post()
                .uri("/api/v1/conversations/{id}/chat", conversationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":\"" + message + "\"}")
                .exchange()
                .expectStatus().isOk();
    }
}
