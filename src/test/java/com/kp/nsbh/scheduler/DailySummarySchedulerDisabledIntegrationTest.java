package com.kp.nsbh.scheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "nsbh.llm.provider=mock",
        "scheduler.dailySummary.enabled=false",
        "scheduler.dailySummary.cron=*/1 * * * * *"
})
class DailySummarySchedulerDisabledIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldNotCreateSchedulerBeanWhenDisabled() {
        assertTrue(applicationContext.getBeansOfType(DailySummaryScheduler.class).isEmpty());
    }

    @Test
    void shouldNotPersistDailySummaryWhenDisabled() throws Exception {
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

        webTestClient.post()
                .uri("/api/v1/conversations/{id}/chat", conversationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":\"hello\"}")
                .exchange()
                .expectStatus().isOk();

        Thread.sleep(1200L);

        byte[] messagesBytes = webTestClient.get()
                .uri("/api/v1/conversations/{id}/messages", conversationId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBodyContent();

        String messagesBody = messagesBytes == null ? "[]" : new String(messagesBytes, StandardCharsets.UTF_8);
        JsonNode messages = objectMapper.readTree(messagesBody);
        boolean hasDailySummary = false;
        for (JsonNode message : messages) {
            if ("DAILY_SUMMARY".equals(message.get("type").asText())) {
                hasDailySummary = true;
                break;
            }
        }
        assertFalse(hasDailySummary);
    }
}
