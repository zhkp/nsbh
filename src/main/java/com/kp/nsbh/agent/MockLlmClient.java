package com.kp.nsbh.agent;

import com.kp.nsbh.memory.entity.MessageEntity;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(prefix = "nsbh.llm", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");

    private volatile List<LlmReply> script = null;
    private final AtomicInteger scriptIndex = new AtomicInteger(0);

    public void script(LlmReply... replies) {
        this.script = List.of(replies);
        this.scriptIndex.set(0);
    }

    @Override
    public Mono<LlmReply> firstReply(String userMessage, String model,
                                      List<MessageEntity> memoryWindow) {
        if (script != null) {
            int i = Math.min(scriptIndex.getAndIncrement(), script.size() - 1);
            return Mono.just(script.get(i));
        }
        String lower = userMessage.toLowerCase(Locale.ROOT);
        Matcher matcher = URL_PATTERN.matcher(userMessage);
        if (matcher.find()) {
            String url = matcher.group(1).replaceAll("[),.]+$", "");
            String args = "{\"url\":\"" + url.replace("\"", "\\\"") + "\"}";
            return Mono.just(LlmReply.withTool(
                    new ToolCallRequest(UUID.randomUUID().toString(), "http_get", args)));
        }
        if (lower.contains("time") || lower.contains("时间")) {
            return Mono.just(LlmReply.withTool(
                    new ToolCallRequest(UUID.randomUUID().toString(), "time", "{}")));
        }
        return Mono.just(LlmReply.text("Mock: " + userMessage));
    }

    @Override
    public Mono<String> finalReply(String userMessage, String model,
                                    String toolResult, List<MessageEntity> memoryWindow) {
        return Mono.just("现在时间是: " + toolResult);
    }

    @Override
    public Flux<String> streamFirstReply(String userMessage, String model,
                                          List<MessageEntity> memoryWindow) {
        return firstReply(userMessage, model, memoryWindow)
                .flatMapMany(reply -> {
                    String msg = reply.assistantMessage();
                    if (msg == null || msg.isBlank()) {
                        return Flux.empty();
                    }
                    return Flux.just(msg);
                });
    }

    @Override
    public Mono<String> summarize(List<MessageEntity> messages, String model) {
        return Mono.just("SUMMARY messages=" + messages.size());
    }
}
