package com.kp.nsbh.agent;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.memory.entity.MessageType;
import com.kp.nsbh.memory.repo.MessageRepository;
import com.kp.nsbh.tools.ToolCallStatus;
import com.kp.nsbh.tools.ToolExecutionResult;
import com.kp.nsbh.tools.ToolService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ChatOrchestrator {

    private final LlmClient llmClient;
    private final ToolService toolService;
    private final PromptBuilder promptBuilder;
    private final MemoryService memoryService;
    private final MessageRepository messageRepository;
    private final NsbhProperties properties;

    public ChatOrchestrator(LlmClient llmClient,
                             ToolService toolService,
                             PromptBuilder promptBuilder,
                             MemoryService memoryService,
                             MessageRepository messageRepository,
                             NsbhProperties properties) {
        this.llmClient = llmClient;
        this.toolService = toolService;
        this.promptBuilder = promptBuilder;
        this.memoryService = memoryService;
        this.messageRepository = messageRepository;
        this.properties = properties;
    }

    public Flux<AgentEvent> orchestrate(UUID conversationId, String userMessage, String model) {
        return Flux.defer(() ->
                messageRepository.save(userMessage(conversationId, userMessage))
                        .then(memoryService.maybeCompact(conversationId, model))
                        .thenMany(loop(conversationId, userMessage, model, 0, new ArrayList<>()))
        );
    }

    private Flux<AgentEvent> loop(UUID conversationId,
                                   String userMessage,
                                   String model,
                                   int round,
                                   List<ToolExecutionResult> accumulated) {
        if (round >= properties.getAgent().getMaxToolRounds()) {
            return Flux.just(new AgentEvent.Done("", List.copyOf(accumulated)));
        }

        return promptBuilder.buildPromptWindow(conversationId)
                .flatMapMany(window ->
                        llmClient.firstReply(userMessage, model, window)
                                .onErrorMap(LlmClientException.class,
                                        e -> new org.springframework.web.server.ResponseStatusException(
                                                org.springframework.http.HttpStatus.BAD_GATEWAY, e.getMessage()))
                                .flatMapMany(reply -> {
                                    if (!reply.hasToolCalls()) {
                                        return handleFinalReply(conversationId, reply, accumulated);
                                    }
                                    return handleToolRound(conversationId, userMessage, model,
                                            round, accumulated, reply.toolCalls());
                                })
                );
    }

    private Flux<AgentEvent> handleFinalReply(UUID conversationId,
                                               LlmReply reply,
                                               List<ToolExecutionResult> accumulated) {
        String text = reply.assistantMessage() == null ? "" : reply.assistantMessage();
        return messageRepository.save(assistantMessage(conversationId, text))
                .thenMany(Flux.concat(
                        text.isBlank() ? Flux.empty() : Flux.just(new AgentEvent.TextDelta(text)),
                        Flux.just(new AgentEvent.Done(text, List.copyOf(accumulated)))
                ));
    }

    private Flux<AgentEvent> handleToolRound(UUID conversationId,
                                              String userMessage,
                                              String model,
                                              int round,
                                              List<ToolExecutionResult> accumulated,
                                              List<ToolCallRequest> toolCalls) {
        Flux<AgentEvent> toolStarts = Flux.fromIterable(toolCalls)
                .map(tc -> (AgentEvent) new AgentEvent.ToolStart(tc.toolName(), tc.id()));

        @SuppressWarnings("unchecked")
        Flux<AgentEvent>[] toolFluxes = toolCalls.stream()
                .map(tc -> executeOneTool(conversationId, tc))
                .toArray(Flux[]::new);

        Flux<AgentEvent> toolExecutions = Flux.mergeDelayError(Integer.MAX_VALUE, toolFluxes)
                .collectList()
                .flatMapMany(toolEndEvents -> {
                    List<ToolExecutionResult> results = toolEndEvents.stream()
                            .filter(e -> e instanceof AgentEvent.ToolEnd)
                            .map(e -> {
                                AgentEvent.ToolEnd te = (AgentEvent.ToolEnd) e;
                                return new ToolExecutionResult(
                                        te.toolName(), te.status(), null, te.result(), te.toolCallId());
                            })
                            .toList();
                    List<ToolExecutionResult> newAccumulated = new ArrayList<>(accumulated);
                    newAccumulated.addAll(results);

                    return Flux.fromIterable(toolEndEvents)
                            .concatWith(Flux.defer(() ->
                                    loop(conversationId, userMessage, model, round + 1, newAccumulated)));
                });

        return toolStarts.concatWith(toolExecutions);
    }

    private Flux<AgentEvent> executeOneTool(UUID conversationId, ToolCallRequest tc) {
        return toolService.execute(conversationId.toString(), tc.toolName(), tc.inputJson(), tc.id())
                .flatMapMany(result -> {
                    AgentEvent.ToolEnd toolEnd = new AgentEvent.ToolEnd(
                            result.toolName(), result.toolCallId(), result.status(), result.result());
                    return messageRepository.save(toolMessage(conversationId, result))
                            .thenReturn((AgentEvent) toolEnd);
                });
    }

    private MessageEntity userMessage(UUID conversationId, String content) {
        return newMessage(conversationId, MessageRole.USER, MessageType.NORMAL, content, null, null);
    }

    private MessageEntity assistantMessage(UUID conversationId, String content) {
        return newMessage(conversationId, MessageRole.ASSISTANT, MessageType.NORMAL, content, null, null);
    }

    private MessageEntity toolMessage(UUID conversationId, ToolExecutionResult result) {
        return newMessage(conversationId, MessageRole.TOOL, MessageType.NORMAL,
                result.result(), result.toolName(), result.toolCallId());
    }

    private MessageEntity newMessage(UUID conversationId, MessageRole role, MessageType type,
                                      String content, String toolName, String toolCallId) {
        MessageEntity msg = new MessageEntity();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setType(type);
        msg.setContent(content == null ? "" : content);
        msg.setToolName(toolName);
        msg.setToolCallId(toolCallId);
        return msg;
    }
}
