package com.kp.nsbh.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@NsbhTool(
        name = "think",
        description = "Record a reasoning step. Returns the thought unchanged.",
        schema = "{\"type\":\"object\",\"properties\":{\"thought\":{\"type\":\"string\"}},\"required\":[\"thought\"]}"
)
public class ThinkTool implements Tool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<String> execute(String inputJson) {
        return Mono.fromCallable(() -> {
            JsonNode root = objectMapper.readTree(inputJson == null ? "{}" : inputJson);
            String thought = root.path("thought").asText("");
            return objectMapper.writeValueAsString(Map.of("thought", thought, "status", "ok"));
        });
    }
}
