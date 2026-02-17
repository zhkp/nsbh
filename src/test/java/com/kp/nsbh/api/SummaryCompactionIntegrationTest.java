package com.kp.nsbh.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "nsbh.llm.provider=mock",
        "nsbh.memory.compactAfter=2",
        "nsbh.memory.window=2"
})
class SummaryCompactionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCompactAndPersistSummaryMessage() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/conversations"))
                .andExpect(status().isOk())
                .andReturn();

        String conversationId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("conversationId")
                .asText();

        sendChat(conversationId, "hello-1");
        sendChat(conversationId, "hello-2");
        sendChat(conversationId, "hello-3");

        MvcResult messagesResult = mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode messages = objectMapper.readTree(messagesResult.getResponse().getContentAsString());

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

    private void sendChat(String conversationId, String message) throws Exception {
        mockMvc.perform(post("/api/v1/conversations/{id}/chat", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andExpect(status().isOk());
    }
}
