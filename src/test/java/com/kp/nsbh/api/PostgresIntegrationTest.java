package com.kp.nsbh.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureWebTestClient
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
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://"
                + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driverClassName", postgres::getDriverClassName);
    }

    @Autowired
    private WebTestClient webTestClient;

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
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":\"what time is it\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.assistantMessage").isNotEmpty()
                .jsonPath("$.toolCalls[0].toolName").isEqualTo("time");
    }
}
