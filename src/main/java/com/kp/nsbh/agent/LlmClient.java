package com.kp.nsbh.agent;

import com.kp.nsbh.memory.entity.MessageEntity;
import java.util.List;
import reactor.core.publisher.Mono;

public interface LlmClient {
    Mono<LlmReply> firstReply(String userMessage, String model, List<MessageEntity> memoryWindow);

    Mono<String> finalReply(String userMessage, String model, String toolResult, List<MessageEntity> memoryWindow);

    Mono<String> summarize(List<MessageEntity> messages, String model);
}
