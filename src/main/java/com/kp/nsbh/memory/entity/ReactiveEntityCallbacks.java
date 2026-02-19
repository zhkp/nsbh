package com.kp.nsbh.memory.entity;

import java.time.Instant;
import java.util.UUID;
import org.reactivestreams.Publisher;
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ReactiveEntityCallbacks implements BeforeConvertCallback<Object> {

    @Override
    public Publisher<Object> onBeforeConvert(Object entity, org.springframework.data.relational.core.sql.SqlIdentifier table) {
        if (entity instanceof ConversationEntity conversation) {
            Instant now = Instant.now();
            if (conversation.getId() == null) {
                conversation.setId(UUID.randomUUID());
            }
            if (conversation.getCreatedAt() == null) {
                conversation.setCreatedAt(now);
            }
            conversation.setUpdatedAt(now);
            return Mono.just(conversation);
        }

        if (entity instanceof MessageEntity message) {
            if (message.getId() == null) {
                message.setId(UUID.randomUUID());
            }
            if (message.getType() == null) {
                message.setType(MessageType.NORMAL);
            }
            if (message.getCreatedAt() == null) {
                message.setCreatedAt(Instant.now());
            }
            return Mono.just(message);
        }

        return Mono.just(entity);
    }
}
