package com.kp.nsbh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MockLlmClientScriptTest {

    @Test
    void scriptReturnsRepliesInOrder() {
        MockLlmClient client = new MockLlmClient();
        client.script(
                LlmReply.withTool(new ToolCallRequest("id1", "time", "{}")),
                LlmReply.withTool(new ToolCallRequest("id2", "time", "{}")),
                LlmReply.text("done")
        );

        LlmReply r1 = client.firstReply("msg", "model", List.of()).block();
        LlmReply r2 = client.firstReply("msg", "model", List.of()).block();
        LlmReply r3 = client.firstReply("msg", "model", List.of()).block();

        assertTrue(r1.hasToolCalls());
        assertEquals("id1", r1.toolCalls().get(0).id());
        assertTrue(r2.hasToolCalls());
        assertEquals("id2", r2.toolCalls().get(0).id());
        assertFalse(r3.hasToolCalls());
        assertEquals("done", r3.assistantMessage());
    }

    @Test
    void scriptClampsAtLastReplyWhenExhausted() {
        MockLlmClient client = new MockLlmClient();
        client.script(LlmReply.text("only"));

        client.firstReply("msg", "m", List.of()).block();
        LlmReply r2 = client.firstReply("msg", "m", List.of()).block();

        assertEquals("only", r2.assistantMessage());
    }

    @Test
    void streamFirstReplyEmitsAssistantMessageAsOneDelta() {
        MockLlmClient client = new MockLlmClient();
        client.script(LlmReply.text("hello world"));

        List<String> chunks = client.streamFirstReply("msg", "m", List.of())
                .collectList().block();

        assertFalse(chunks.isEmpty());
        String full = String.join("", chunks);
        assertEquals("hello world", full);
    }
}
