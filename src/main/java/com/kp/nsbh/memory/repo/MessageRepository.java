package com.kp.nsbh.memory.repo;

import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {
    List<MessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    List<MessageEntity> findByConversationIdAndTypeOrderByCreatedAtAsc(UUID conversationId, MessageType type);

    List<MessageEntity> findByConversationIdAndTypeOrderByCreatedAtDesc(UUID conversationId, MessageType type);

    long countByConversationIdAndType(UUID conversationId, MessageType type);

    List<MessageEntity> findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID conversationId, Instant since);

    @Query("select distinct m.conversation.id from MessageEntity m where m.createdAt >= :since")
    List<UUID> findConversationIdsWithMessagesSince(@Param("since") Instant since);
}
