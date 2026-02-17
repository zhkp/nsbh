package com.kp.nsbh.memory.entity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ReactiveEntityCallbacksTest {

    private final ReactiveEntityCallbacks callbacks = new ReactiveEntityCallbacks();

    @Test
    void shouldPopulateConversationDefaults() {
        ConversationEntity entity = new ConversationEntity();

        ConversationEntity result = (ConversationEntity) Mono.from(callbacks.onBeforeConvert(entity, null)).block();

        assertNotNull(result.getId());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    void shouldKeepConversationCreatedAtWhenAlreadySet() {
        ConversationEntity entity = new ConversationEntity();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        entity.setCreatedAt(createdAt);

        ConversationEntity result = (ConversationEntity) Mono.from(callbacks.onBeforeConvert(entity, null)).block();

        assertSame(createdAt, result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    void shouldPopulateMessageDefaults() {
        MessageEntity entity = new MessageEntity();

        MessageEntity result = (MessageEntity) Mono.from(callbacks.onBeforeConvert(entity, null)).block();

        assertNotNull(result.getId());
        assertNotNull(result.getCreatedAt());
        assertTrue(result.getType() == MessageType.NORMAL);
    }

    @Test
    void shouldPassThroughUnknownEntity() {
        String entity = "x";
        Object result = Mono.from(callbacks.onBeforeConvert(entity, null)).block();
        assertSame(entity, result);
    }
}
