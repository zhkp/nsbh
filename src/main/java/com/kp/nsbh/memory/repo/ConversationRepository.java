package com.kp.nsbh.memory.repo;

import com.kp.nsbh.memory.entity.ConversationEntity;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * 对话数据访问接口，提供对话实体的持久化操作。
 *
 * <p>该接口继承自Spring Data的ReactiveCrudRepository，支持响应式编程模型。
 * 目前提供基础的CRUD操作，可通过Spring Data的派生查询方法扩展更多查询功能。</p>
 *
 * <p>对话是消息的分组容器，每个对话包含一组相关的消息记录。</p>
 *
 * @author NSBH Team
 * @since 1.0.0
 * @see ConversationEntity
 * @see MessageRepository
 */
public interface ConversationRepository extends ReactiveCrudRepository<ConversationEntity, UUID> {
    // 基础CRUD操作由父接口提供
    // 如需自定义查询方法，可在此添加
}
