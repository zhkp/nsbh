package com.kp.nsbh.scheduler;

import com.kp.nsbh.agent.LlmClient;
import com.kp.nsbh.agent.LlmClientException;
import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.logging.JsonLogFormatter;
import com.kp.nsbh.memory.entity.ConversationEntity;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.memory.entity.MessageType;
import com.kp.nsbh.memory.repo.ConversationRepository;
import com.kp.nsbh.memory.repo.MessageRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "scheduler.dailySummary", name = "enabled", havingValue = "true")
public class DailySummaryScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(DailySummaryScheduler.class);

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final LlmClient llmClient;
    private final NsbhProperties properties;

    public DailySummaryScheduler(MessageRepository messageRepository,
                                 ConversationRepository conversationRepository,
                                 LlmClient llmClient,
                                 NsbhProperties properties) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.llmClient = llmClient;
        this.properties = properties;
    }

    @Scheduled(cron = "${scheduler.dailySummary.cron}")
    @Transactional
    public void scheduledRun() {
        runDailySummary();
    }

    @Transactional
    public void runDailySummary() {
        String jobRunId = UUID.randomUUID().toString();
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        List<UUID> conversationIds = messageRepository.findConversationIdsWithMessagesSince(since);
        LOG.info(JsonLogFormatter.json(JsonLogFormatter.fields(
                "event", "daily_summary_start",
                "jobRunId", jobRunId,
                "conversationCount", conversationIds.size()
        )));

        for (UUID conversationId : conversationIds) {
            ConversationEntity conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) {
                continue;
            }

            List<MessageEntity> messages = messageRepository.findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(
                    conversationId,
                    since
            );
            if (messages.isEmpty()) {
                continue;
            }

            String summary;
            try {
                summary = llmClient.summarize(messages, properties.getLlm().getModelDefault());
            } catch (LlmClientException e) {
                LOG.warn(JsonLogFormatter.json(JsonLogFormatter.fields(
                        "event", "daily_summary_failed",
                        "jobRunId", jobRunId,
                        "conversationId", conversationId,
                        "reason", e.getMessage()
                )));
                continue;
            }

            MessageEntity summaryMessage = new MessageEntity();
            summaryMessage.setConversation(conversation);
            summaryMessage.setRole(MessageRole.SYSTEM);
            summaryMessage.setType(MessageType.DAILY_SUMMARY);
            summaryMessage.setContent(summary == null ? "" : summary);
            messageRepository.save(summaryMessage);

            LOG.info(JsonLogFormatter.json(JsonLogFormatter.fields(
                    "event", "daily_summary_saved",
                    "jobRunId", jobRunId,
                    "conversationId", conversationId,
                    "messageCount", messages.size()
            )));
        }

        LOG.info(JsonLogFormatter.json(JsonLogFormatter.fields(
                "event", "daily_summary_end",
                "jobRunId", jobRunId
        )));
    }
}
