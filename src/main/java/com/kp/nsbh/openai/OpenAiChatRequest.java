package com.kp.nsbh.openai;

import java.util.List;

public record OpenAiChatRequest(String model, List<OpenAiMessage> messages, Boolean stream) {}
