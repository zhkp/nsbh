package com.kp.nsbh.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.profiles.active=postgres",
        "nsbh.llm.provider=mock",
        "nsbh.tools.allowed[0]=time"
})
class PostgresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("nsbh")
            .withUsername("nsbh")
            .withPassword("nsbh");

    @DynamicPropertySource
    static void postgresProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driverClassName", postgres::getDriverClassName);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldApplyFlywayAndServeConversationApis() throws Exception {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class
        );
        assertTrue(count != null && count >= 1);

        MvcResult createResult = mockMvc.perform(post("/api/v1/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").isNotEmpty())
                .andReturn();

        JsonNode createBody = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String conversationId = createBody.get("conversationId").asText();

        mockMvc.perform(post("/api/v1/conversations/{id}/chat", conversationId)
                        .contentType("application/json")
                        .content("{\"message\":\"what time is it\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage").isNotEmpty())
                .andExpect(jsonPath("$.toolCalls[0].toolName").value("time"));
    }
}
