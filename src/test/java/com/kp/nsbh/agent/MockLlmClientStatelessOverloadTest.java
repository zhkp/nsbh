package com.kp.nsbh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.memory.entity.MessageType;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockLlmClientStatelessOverloadTest {

    private final MockLlmClient llm = new MockLlmClient();

    private MessageEntity userEntity(String content) {
        MessageEntity e = new MessageEntity();
        e.setRole(MessageRole.USER);
        e.setType(MessageType.NORMAL);
        e.setContent(content);
        return e;
    }

    @Test
    void noScriptFirstReplyReturnsLastUserContent() {
        List<MessageEntity> messages = List.of(userEntity("hello"));
        LlmReply reply = llm.firstReply(messages, "mock").block();
        assertEquals("Mock: hello", reply.assistantMessage());
    }

    @Test
    void noScriptNoUserMessageReturnsEmptyPrefix() {
        List<MessageEntity> messages = List.of();
        LlmReply reply = llm.firstReply(messages, "mock").block();
        assertEquals("Mock: ", reply.assistantMessage());
    }

    @Test
    void scriptModeFirstReplyUsesScript() {
        llm.script(LlmReply.text("scripted reply"));
        List<MessageEntity> messages = List.of(userEntity("hi"));
        LlmReply reply = llm.firstReply(messages, "mock").block();
        assertEquals("scripted reply", reply.assistantMessage());
    }

    @Test
    void streamFirstReplyNonBlankReturnsOneToken() {
        llm.script(LlmReply.text("streamed"));
        List<MessageEntity> messages = List.of(userEntity("hi"));
        List<String> tokens = llm.streamFirstReply(messages, "mock").collectList().block();
        assertEquals(List.of("streamed"), tokens);
    }

    @Test
    void streamFirstReplyBlankReplyProducesEmptyFlux() {
        llm.script(LlmReply.text(""));
        List<MessageEntity> messages = List.of(userEntity("hi"));
        List<String> tokens = llm.streamFirstReply(messages, "mock").collectList().block();
        assertTrue(tokens.isEmpty());
    }

    @Test
    void streamFirstReplyNullReplyProducesEmptyFlux() {
        llm.script(LlmReply.text(null));
        List<MessageEntity> messages = List.of(userEntity("hi"));
        List<String> tokens = llm.streamFirstReply(messages, "mock").collectList().block();
        assertTrue(tokens.isEmpty());
    }
}
