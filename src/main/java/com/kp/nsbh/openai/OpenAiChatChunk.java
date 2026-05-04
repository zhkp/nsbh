package com.kp.nsbh.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenAiChatChunk(
        String id,
        String object,
        long created,
        String model,
        List<ChunkChoice> choices
) {
    public record ChunkChoice(
            int index,
            Delta delta,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Delta(String role, String content) {}
}
