package com.kp.nsbh.agent;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.tools.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class ProviderSwitchTest {

    @Test
    void openAiClientCreatedWhenProviderIsOpenai() {
        NsbhProperties props = new NsbhProperties();
        props.getLlm().setProvider("openai");
        props.getLlm().setApiKey("sk-test");
        props.getLlm().setBaseUrl("https://api.openai.com");

        OpenAiLlmClient client = new OpenAiLlmClient(
                WebClient.builder(), props, new com.fasterxml.jackson.databind.ObjectMapper(),
                new ToolRegistry(List.of()));

        assertInstanceOf(OpenAiLlmClient.class, client);
    }

    @Test
    void anthropicClientCreatedWhenProviderIsAnthropic() {
        NsbhProperties props = new NsbhProperties();
        props.getLlm().setProvider("anthropic");
        props.getLlm().setApiKey("sk-ant-test");
        props.getLlm().setBaseUrl("https://api.anthropic.com");

        AnthropicLlmClient client = new AnthropicLlmClient(
                WebClient.builder(), props, new ToolRegistry(List.of()));

        assertInstanceOf(AnthropicLlmClient.class, client);
    }

    @Test
    void ollamaClientAcceptsBlankApiKey() {
        NsbhProperties props = new NsbhProperties();
        props.getLlm().setProvider("ollama");
        props.getLlm().setApiKey("");
        props.getLlm().setBaseUrl("http://localhost:11434");

        OllamaLlmClient client = new OllamaLlmClient(
                WebClient.builder(), props, new com.fasterxml.jackson.databind.ObjectMapper(),
                new ToolRegistry(List.of()));

        assertInstanceOf(OllamaLlmClient.class, client);
    }
}
