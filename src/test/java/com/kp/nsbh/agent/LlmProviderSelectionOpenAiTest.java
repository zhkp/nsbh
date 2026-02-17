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
        "nsbh.llm.provider=openai",
        "nsbh.llm.apiKey=test-key"
})
class LlmProviderSelectionOpenAiTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private LlmClient llmClient;

    @Test
    void shouldUseOpenAiProviderWhenConfigured() {
        assertInstanceOf(OpenAiLlmClient.class, llmClient);
        assertFalse(context.containsBeanDefinition("mockLlmClient"));
    }
}
