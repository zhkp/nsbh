package com.kp.nsbh.api;

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
        "nsbh.tools.allowed[0]=dummy"
})
class ToolRejectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldMapNotAllowedReason() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/conversations"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createBody = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String conversationId = createBody.get("conversationId").asText();

        mockMvc.perform(post("/api/v1/conversations/{id}/chat", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"what time\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolCalls[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.toolCalls[0].reason").value("NOT_ALLOWED"));
    }
}
