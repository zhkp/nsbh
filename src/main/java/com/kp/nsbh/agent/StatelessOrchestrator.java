package com.kp.nsbh.agent;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.memory.entity.MessageType;
import com.kp.nsbh.openai.OpenAiMessage;
import com.kp.nsbh.tools.ToolExecutionResult;
import com.kp.nsbh.tools.ToolService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class StatelessOrchestrator {

    private final LlmClient llmClient;
    private final ToolService toolService;
    private final NsbhProperties properties;

    public StatelessOrchestrator(LlmClient llmClient,
                                 ToolService toolService,
                                 NsbhProperties properties) {
        this.llmClient = llmClient;
        this.toolService = toolService;
        this.properties = properties;
    }

    public Mono<String> chat(List<OpenAiMessage> messages, String model) {
        return loop(toEntities(messages), model, 0);
    }

    public Flux<String> stream(List<OpenAiMessage> messages, String model) {
        return streamLoop(toEntities(messages), model, 0);
    }

    private Mono<String> loop(List<MessageEntity> messages, String model, int round) {
        if (round >= properties.getAgent().getMaxToolRounds()) {
            return Mono.just(lastAssistantContent(messages));
        }
        return llmClient.firstReply(messages, model)
                .flatMap(reply -> {
                    if (!reply.hasToolCalls()) {
                        return Mono.just(reply.assistantMessage() == null ? "" : reply.assistantMessage());
                    }
                    return executeToolsAndContinue(reply.toolCalls(), messages, model, round);
                });
    }

    private Mono<String> executeToolsAndContinue(List<ToolCallRequest> toolCalls,
                                                  List<MessageEntity> messages,
                                                  String model,
                                                  int round) {
        String sessionId = UUID.randomUUID().toString();
        return Flux.fromIterable(toolCalls)
                .flatMap(tc -> toolService.execute(sessionId, tc.toolName(), tc.inputJson(), tc.id()))
                .collectList()
                .flatMap(results -> {
                    List<MessageEntity> next = new ArrayList<>(messages);
                    for (ToolExecutionResult r : results) {
                        next.add(toolEntity(r));
                    }
                    return loop(next, model, round + 1);
                });
    }

    private Flux<String> streamLoop(List<MessageEntity> messages, String model, int round) {
        if (round >= properties.getAgent().getMaxToolRounds()) {
            String last = lastAssistantContent(messages);
            return last.isBlank() ? Flux.empty() : Flux.just(last);
        }
        return llmClient.firstReply(messages, model)
                .flatMapMany(reply -> {
                    if (!reply.hasToolCalls()) {
                        return llmClient.streamFirstReply(messages, model);
                    }
                    String sessionId = UUID.randomUUID().toString();
                    return Flux.fromIterable(reply.toolCalls())
                            .flatMap(tc -> toolService.execute(
                                    sessionId, tc.toolName(), tc.inputJson(), tc.id()))
                            .collectList()
                            .flatMapMany(results -> {
                                List<MessageEntity> next = new ArrayList<>(messages);
                                for (ToolExecutionResult r : results) {
                                    next.add(toolEntity(r));
                                }
                                return streamLoop(next, model, round + 1);
                            });
                });
    }

    private String lastAssistantContent(List<MessageEntity> messages) {
        return messages.stream()
                .filter(m -> m.getRole() == MessageRole.ASSISTANT)
                .reduce((a, b) -> b)
                .map(MessageEntity::getContent)
                .orElse("");
    }

    private List<MessageEntity> toEntities(List<OpenAiMessage> messages) {
        return messages.stream().map(m -> {
            MessageEntity e = new MessageEntity();
            e.setRole(mapRole(m.role()));
            e.setType(MessageType.NORMAL);
            e.setContent(m.content() == null ? "" : m.content());
            return e;
        }).collect(Collectors.toList());
    }

    private MessageRole mapRole(String role) {
        return switch (role) {
            case "assistant" -> MessageRole.ASSISTANT;
            case "system" -> MessageRole.SYSTEM;
            case "tool" -> MessageRole.TOOL;
            default -> MessageRole.USER;
        };
    }

    private MessageEntity toolEntity(ToolExecutionResult r) {
        MessageEntity e = new MessageEntity();
        e.setRole(MessageRole.TOOL);
        e.setType(MessageType.NORMAL);
        e.setContent(r.result());
        e.setToolName(r.toolName());
        e.setToolCallId(r.toolCallId());
        return e;
    }
}
