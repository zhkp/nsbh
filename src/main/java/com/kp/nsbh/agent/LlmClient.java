package com.kp.nsbh.agent;

import com.kp.nsbh.memory.entity.MessageEntity;
import java.util.List;

public interface LlmClient {
    LlmReply firstReply(String userMessage, String model, List<MessageEntity> memoryWindow);

    String finalReply(String userMessage, String model, String toolResult, List<MessageEntity> memoryWindow);

    String summarize(List<MessageEntity> messages, String model);
}
