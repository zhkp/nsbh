package com.kp.nsbh.memory.repo;

import com.kp.nsbh.memory.entity.ConversationEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {
}
