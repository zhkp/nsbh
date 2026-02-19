package com.kp.nsbh.memory.repo;

import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MessageRepository extends ReactiveCrudRepository<MessageEntity, UUID> {
    Flux<MessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    Flux<MessageEntity> findByConversationIdAndTypeOrderByCreatedAtAsc(UUID conversationId, MessageType type);

    Flux<MessageEntity> findByConversationIdAndTypeOrderByCreatedAtDesc(UUID conversationId, MessageType type);

    Mono<Long> countByConversationIdAndType(UUID conversationId, MessageType type);

    Flux<MessageEntity> findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID conversationId, Instant since);

    @Query("select distinct conversation_id from messages where created_at >= :since")
    Flux<UUID> findConversationIdsWithMessagesSince(Instant since);

    Mono<Long> deleteByConversationIdAndType(UUID conversationId, MessageType type);
}
