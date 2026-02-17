package com.kp.nsbh.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ToolsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listToolsShouldReturnTimeMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/tools"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"name\":\"time\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"name\":\"http_get\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"requiredPermissions\":[\"NET_HTTP\"]")));
    }
}
