package com.kp.nsbh.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        "nsbh.tools.allowed[0]=time"
})
class ConversationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createChatAndReadMessagesShouldWork() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").isNotEmpty())
                .andReturn();

        JsonNode createBody = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String conversationId = createBody.get("conversationId").asText();

        mockMvc.perform(post("/api/v1/conversations/{id}/chat", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"请告诉我当前时间\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage").isNotEmpty())
                .andExpect(jsonPath("$.toolCalls[0].toolName").value("time"))
                .andExpect(jsonPath("$.toolCalls[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.toolCalls[0].reason").value("NONE"));

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[1].role").value("TOOL"))
                .andExpect(jsonPath("$[2].role").value("ASSISTANT"));
    }
}
