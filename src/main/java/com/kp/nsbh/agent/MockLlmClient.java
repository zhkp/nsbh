package com.kp.nsbh.agent;

import com.kp.nsbh.memory.entity.MessageEntity;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(prefix = "nsbh.llm", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");

    @Override
    public Mono<LlmReply> firstReply(String userMessage, String model, List<MessageEntity> memoryWindow) {
        String lower = userMessage.toLowerCase(Locale.ROOT);
        Matcher matcher = URL_PATTERN.matcher(userMessage);
        if (matcher.find()) {
            String url = matcher.group(1).replaceAll("[),.]+$", "");
            String args = "{\"url\":\"" + url.replace("\"", "\\\"") + "\"}";
            return Mono.just(new LlmReply(null, new ToolCallRequest(UUID.randomUUID().toString(), "http_get", args)));
        }
        if (lower.contains("time") || lower.contains("时间")) {
            return Mono.just(new LlmReply(null, new ToolCallRequest(UUID.randomUUID().toString(), "time", "{}")));
        }
        return Mono.just(new LlmReply("Mock: " + userMessage, null));
    }

    @Override
    public Mono<String> finalReply(String userMessage, String model, String toolResult, List<MessageEntity> memoryWindow) {
        return Mono.just("现在时间是: " + toolResult);
    }

    @Override
    public Mono<String> summarize(List<MessageEntity> messages, String model) {
        return Mono.just("SUMMARY messages=" + messages.size());
    }
}
