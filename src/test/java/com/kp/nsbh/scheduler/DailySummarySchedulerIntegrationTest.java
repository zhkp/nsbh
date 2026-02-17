package com.kp.nsbh.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kp.nsbh.agent.ConversationService;
import com.kp.nsbh.memory.entity.ConversationEntity;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageType;
import com.kp.nsbh.memory.repo.ConversationRepository;
import com.kp.nsbh.memory.repo.MessageRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "scheduler.dailySummary.enabled=true",
        "nsbh.llm.provider=mock"
})
class DailySummarySchedulerIntegrationTest {

    @Autowired
    private DailySummaryScheduler scheduler;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationService conversationService;

    @Test
    void manualInvokeShouldPersistDailySummary() {
        ConversationEntity conversation = conversationRepository.save(new ConversationEntity());
        conversationService.chat(conversation.getId(), "hello", null);

        scheduler.runDailySummary();

        List<MessageEntity> all = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        long summaryCount = all.stream().filter(m -> m.getType() == MessageType.DAILY_SUMMARY).count();
        assertEquals(1, summaryCount);

        MessageEntity summary = all.stream().filter(m -> m.getType() == MessageType.DAILY_SUMMARY).findFirst().orElseThrow();
        assertTrue(summary.getContent().startsWith("SUMMARY messages="));
    }
}
