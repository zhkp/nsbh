package com.kp.nsbh.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RequestIdIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void successResponseShouldContainRequestIdHeaderAndBody() throws Exception {
        mockMvc.perform(post("/api/v1/conversations"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void errorResponseShouldContainRequestIdHeaderAndBody() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/{id}/messages", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
