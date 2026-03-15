package com.kp.nsbh.memory.repo;

import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息数据访问接口，提供消息实体的持久化操作。
 *
 * <p>该接口继承自Spring Data的ReactiveCrudRepository，支持响应式编程模型。
 * 提供了按对话ID、消息类型、创建时间等条件查询消息的方法。</p>
 *
 * @author NSBH Team
 * @since 1.0.0
 * @see MessageEntity
 * @see ConversationRepository
 */
public interface MessageRepository extends ReactiveCrudRepository<MessageEntity, UUID> {

    /**
     * 根据对话ID查询消息，按创建时间升序排列。
     *
     * @param conversationId 对话唯一标识符
     * @return 按创建时间升序排列的消息流
     */
    Flux<MessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /**
     * 根据对话ID和消息类型查询消息，按创建时间升序排列。
     *
     * @param conversationId 对话唯一标识符
     * @param type 消息类型（如USER、ASSISTANT、SYSTEM等）
     * @return 按创建时间升序排列的指定类型消息流
     */
    Flux<MessageEntity> findByConversationIdAndTypeOrderByCreatedAtAsc(UUID conversationId, MessageType type);

    /**
     * 根据对话ID和消息类型查询消息，按创建时间降序排列。
     *
     * <p>常用于获取最新的某类型消息，如最新的系统消息。</p>
     *
     * @param conversationId 对话唯一标识符
     * @param type 消息类型（如USER、ASSISTANT、SYSTEM等）
     * @return 按创建时间降序排列的指定类型消息流
     */
    Flux<MessageEntity> findByConversationIdAndTypeOrderByCreatedAtDesc(UUID conversationId, MessageType type);

    /**
     * 统计指定对话中某类型消息的数量。
     *
     * @param conversationId 对话唯一标识符
     * @param type 消息类型
     * @return 指定类型消息的数量
     */
    Mono<Long> countByConversationIdAndType(UUID conversationId, MessageType type);

    /**
     * 查询指定对话中在指定时间之后创建的消息，按创建时间升序排列。
     *
     * <p>常用于增量查询，获取某个时间点之后的新消息。</p>
     *
     * @param conversationId 对话唯一标识符
     * @param since 时间戳，查询在此时间之后创建的消息
     * @return 按创建时间升序排列的消息流
     */
    Flux<MessageEntity> findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID conversationId, Instant since);

    /**
     * 查询在指定时间之后有新消息的所有对话ID。
     *
     * <p>此方法使用自定义SQL查询，返回在指定时间之后有消息更新的对话ID列表。
     * 常用于调度任务中识别需要处理的活跃对话。</p>
     *
     * @param since 时间戳，查询在此时间之后有消息的对话
     * @return 符合条件的对话ID流
     */
    @Query("select distinct conversation_id from messages where created_at >= :since")
    Flux<UUID> findConversationIdsWithMessagesSince(Instant since);

    /**
     * 删除指定对话中某类型的所有消息。
     *
     * <p>常用于清理临时消息或特定类型的历史记录。</p>
     *
     * @param conversationId 对话唯一标识符
     * @param type 要删除的消息类型
     * @return 被删除的消息数量
     */
    Mono<Long> deleteByConversationIdAndType(UUID conversationId, MessageType type);
}
