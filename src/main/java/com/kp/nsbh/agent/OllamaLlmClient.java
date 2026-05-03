package com.kp.nsbh.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.tools.ToolRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@ConditionalOnProperty(prefix = "nsbh.llm", name = "provider", havingValue = "ollama")
public class OllamaLlmClient extends OpenAiLlmClient {

    public OllamaLlmClient(WebClient.Builder webClientBuilder,
                            NsbhProperties properties,
                            ObjectMapper objectMapper,
                            ToolRegistry toolRegistry) {
        super(buildOllamaProperties(properties), webClientBuilder, objectMapper, toolRegistry);
    }

    private static NsbhProperties buildOllamaProperties(NsbhProperties original) {
        NsbhProperties props = new NsbhProperties();
        String baseUrl = original.getLlm().getBaseUrl();
        props.getLlm().setBaseUrl(baseUrl == null || baseUrl.isBlank()
                ? "http://localhost:11434" : baseUrl);
        props.getLlm().setApiKey("ollama");
        props.getLlm().setModelDefault(original.getLlm().getModelDefault());
        props.getLlm().setTimeoutMs(original.getLlm().getTimeoutMs());
        return props;
    }
}
