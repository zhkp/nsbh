package com.kp.nsbh.scheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "nsbh.llm.provider=mock",
        "scheduler.dailySummary.enabled=false",
        "scheduler.dailySummary.cron=*/1 * * * * *"
})
class DailySummarySchedulerDisabledIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldNotCreateSchedulerBeanWhenDisabled() {
        assertTrue(applicationContext.getBeansOfType(DailySummaryScheduler.class).isEmpty());
    }

    @Test
    void shouldNotPersistDailySummaryWhenDisabled() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/conversations"))
                .andExpect(status().isOk())
                .andReturn();

        String conversationId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("conversationId")
                .asText();

        mockMvc.perform(post("/api/v1/conversations/{id}/chat", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk());

        Thread.sleep(1200L);

        MvcResult messagesResult = mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode messages = objectMapper.readTree(messagesResult.getResponse().getContentAsString());
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
