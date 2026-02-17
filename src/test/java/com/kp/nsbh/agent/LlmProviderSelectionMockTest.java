package com.kp.nsbh.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "nsbh.llm.provider=mock"
})
class LlmProviderSelectionMockTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private LlmClient llmClient;

    @Test
    void shouldUseMockProviderWhenConfigured() {
        assertInstanceOf(MockLlmClient.class, llmClient);
        assertFalse(context.containsBeanDefinition("openAiLlmClient"));
    }
}
