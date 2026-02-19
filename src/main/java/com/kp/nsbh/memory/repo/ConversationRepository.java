package com.kp.nsbh.memory.repo;

import com.kp.nsbh.memory.entity.ConversationEntity;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ConversationRepository extends ReactiveCrudRepository<ConversationEntity, UUID> {
}
