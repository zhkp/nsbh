package com.kp.nsbh.scheduler;

import com.kp.nsbh.agent.LlmClient;
import com.kp.nsbh.agent.LlmClientException;
import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.logging.JsonLogFormatter;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    public void scheduledRun() {
        runDailySummary().subscribe();
    }

    public Mono<Void> runDailySummary() {
        String jobRunId = UUID.randomUUID().toString();
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        return messageRepository.findConversationIdsWithMessagesSince(since)
                .collectList()
                .flatMap(ids -> {
                    LOG.info(JsonLogFormatter.json(JsonLogFormatter.fields(
                            "event", "daily_summary_start",
                            "jobRunId", jobRunId,
                            "conversationCount", ids.size()
                    )));
                    return Flux.fromIterable(ids).concatMap(id -> summarizeConversation(jobRunId, id, since)).then();
                })
                .doOnSuccess(ignored -> LOG.info(JsonLogFormatter.json(JsonLogFormatter.fields(
                        "event", "daily_summary_end",
                        "jobRunId", jobRunId
                ))));
    }

    private Mono<Void> summarizeConversation(String jobRunId, UUID conversationId, Instant since) {
        return conversationRepository.findById(conversationId)
                .flatMap(conversation -> messageRepository
                        .findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(conversationId, since)
                        .collectList()
                        .flatMap(messages -> {
                            if (messages.isEmpty()) {
                                return Mono.empty();
                            }
                            return summarize(messages).flatMap(summary -> {
                                MessageEntity summaryMessage = new MessageEntity();
                                summaryMessage.setConversationId(conversationId);
                                summaryMessage.setRole(MessageRole.SYSTEM);
                                summaryMessage.setType(MessageType.DAILY_SUMMARY);
                                summaryMessage.setContent(summary == null ? "" : summary);
                                return messageRepository.save(summaryMessage).then(
                                        Mono.fromRunnable(() -> LOG.info(JsonLogFormatter.json(JsonLogFormatter.fields(
                                                "event", "daily_summary_saved",
                                                "jobRunId", jobRunId,
                                                "conversationId", conversationId,
                                                "messageCount", messages.size()
                                        ))))
                                );
                            });
                        }))
                .onErrorResume(LlmClientException.class, e -> {
                    LOG.warn(JsonLogFormatter.json(JsonLogFormatter.fields(
                            "event", "daily_summary_failed",
                            "jobRunId", jobRunId,
                            "conversationId", conversationId,
                            "reason", e.getMessage()
                    )));
                    return Mono.empty();
                })
                .then();
    }

    private Mono<String> summarize(List<MessageEntity> messages) {
        return llmClient.summarize(messages, properties.getLlm().getModelDefault());
    }
}
