package com.kp.nsbh.agent;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.ConversationEntity;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.memory.entity.MessageType;
import com.kp.nsbh.memory.repo.ConversationRepository;
import com.kp.nsbh.memory.repo.MessageRepository;
import com.kp.nsbh.tools.ToolExecutionResult;
import com.kp.nsbh.tools.ToolService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final NsbhProperties properties;
    private final LlmClient llmClient;
    private final ToolService toolService;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository,
                               NsbhProperties properties,
                               LlmClient llmClient,
                               ToolService toolService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.properties = properties;
        this.llmClient = llmClient;
        this.toolService = toolService;
    }

    public Mono<ConversationEntity> createConversation() {
        return conversationRepository.save(new ConversationEntity());
    }

    public Mono<ChatResult> chat(UUID conversationId, String userMessage, String model) {
        String modelToUse = model == null || model.isBlank() ? properties.getLlm().getModelDefault() : model;

        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found")))
                .flatMap(conversation -> messageRepository.save(
                                newMessage(conversation.getId(), MessageRole.USER, MessageType.NORMAL, userMessage, null, null))
                        .then(maybeCompactMemory(conversation.getId(), modelToUse))
                        .then(buildPromptWindow(conversation.getId()))
                        .flatMap(promptWindow -> firstReply(userMessage, modelToUse, promptWindow))
                        .flatMap(firstReply -> executeAndReply(conversation.getId(), userMessage, modelToUse, firstReply))
                );
    }

    public Flux<MessageEntity> getMessages(UUID conversationId) {
        return conversationRepository.existsById(conversationId)
                .flatMapMany(exists -> {
                    if (!exists) {
                        return Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
                    }
                    return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
                });
    }

    public Mono<List<MessageEntity>> getPromptWindow(UUID conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .collectList()
                .map(all -> {
                    int window = properties.getMemory().getWindow();
                    if (all.size() <= window) {
                        return all;
                    }
                    return all.subList(all.size() - window, all.size());
                });
    }

    private Mono<ChatResult> executeAndReply(UUID conversationId,
                                             String userMessage,
                                             String model,
                                             LlmReply firstReply) {
        ToolCallRequest toolCall = firstReply.toolCall();
        if (toolCall == null) {
            String assistantMessage = firstReply.assistantMessage() == null ? "" : firstReply.assistantMessage();
            return messageRepository.save(newMessage(
                            conversationId,
                            MessageRole.ASSISTANT,
                            MessageType.NORMAL,
                            assistantMessage,
                            null,
                            null
                    ))
                    .thenReturn(new ChatResult(assistantMessage, List.of()));
        }

        return executeTool(conversationId, toolCall)
                .flatMap(toolResult -> messageRepository.save(newMessage(
                                conversationId,
                                MessageRole.TOOL,
                                MessageType.NORMAL,
                                toolResult.result(),
                                toolResult.toolName(),
                                toolResult.toolCallId()
                        ))
                        .then(buildPromptWindow(conversationId))
                        .flatMap(promptWindow -> finalReply(
                                userMessage,
                                model,
                                toolResult.result(),
                                promptWindow
                        ))
                        .flatMap(assistantMessage -> messageRepository.save(newMessage(
                                                conversationId,
                                                MessageRole.ASSISTANT,
                                                MessageType.NORMAL,
                                                assistantMessage,
                                                null,
                                                null
                                        ))
                                        .thenReturn(new ChatResult(assistantMessage, List.of(toolResult)))
                        )
                );
    }

    private Mono<ToolExecutionResult> executeTool(UUID conversationId, ToolCallRequest request) {
        return toolService.execute(
                conversationId.toString(),
                request.toolName(),
                request.inputJson(),
                request.id()
        );
    }

    private Mono<LlmReply> firstReply(String userMessage, String model, List<MessageEntity> promptWindow) {
        return llmClient.firstReply(userMessage, model, promptWindow)
                .onErrorMap(LlmClientException.class, e -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage()));
    }

    private Mono<String> finalReply(String userMessage, String model, String toolResult, List<MessageEntity> promptWindow) {
        return llmClient.finalReply(userMessage, model, toolResult, promptWindow)
                .onErrorMap(LlmClientException.class, e -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage()));
    }

    private Mono<String> summarize(List<MessageEntity> messages, String model) {
        return llmClient.summarize(messages, model)
                .onErrorResume(LlmClientException.class, e -> Mono.empty());
    }

    private Mono<Void> maybeCompactMemory(UUID conversationId, String model) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .collectList()
                .flatMap(allMessages -> {
                    List<MessageEntity> normals = allMessages.stream()
                            .filter(message -> message.getType() == MessageType.NORMAL)
                            .toList();
                    if (normals.size() <= properties.getMemory().getCompactAfter()) {
                        return Mono.empty();
                    }
                    List<MessageEntity> summaries = allMessages.stream()
                            .filter(message -> message.getType() == MessageType.SUMMARY)
                            .toList();

                    return summarize(normals, model)
                            .flatMap(summaryText -> {
                                return Flux.fromIterable(summaries)
                                        .flatMap(summary -> messageRepository.deleteById(summary.getId()))
                                        .then()
                                        .then(messageRepository.save(newMessage(
                                                conversationId,
                                                MessageRole.SYSTEM,
                                                MessageType.SUMMARY,
                                                summaryText,
                                                null,
                                                null
                                        )))
                                        .then();
                            });
                })
                .then();
    }

    private Mono<List<MessageEntity>> buildPromptWindow(UUID conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .collectList()
                .map(allMessages -> {
                    MessageEntity summary = null;
                    List<MessageEntity> normals = new ArrayList<>();
                    for (MessageEntity message : allMessages) {
                        if (message.getType() == MessageType.SUMMARY) {
                            summary = message;
                        } else if (message.getType() == MessageType.NORMAL) {
                            normals.add(message);
                        }
                    }

                    int window = properties.getMemory().getWindow();
                    if (normals.size() <= window) {
                        return assemblePrompt(summary, normals);
                    }
                    List<MessageEntity> limited = normals.subList(normals.size() - window, normals.size());
                    return assemblePrompt(summary, limited);
                });
    }

    private List<MessageEntity> assemblePrompt(MessageEntity summary, List<MessageEntity> normals) {
        List<MessageEntity> prompt = new ArrayList<>();
        prompt.add(systemPromptMessage());
        if (summary != null) {
            prompt.add(summary);
        }
        prompt.addAll(normals);
        return prompt;
    }

    private MessageEntity systemPromptMessage() {
        MessageEntity message = new MessageEntity();
        message.setRole(MessageRole.SYSTEM);
        message.setType(MessageType.NORMAL);
        message.setContent(properties.getMemory().getSystemPrompt());
        return message;
    }

    private MessageEntity newMessage(UUID conversationId,
                                     MessageRole role,
                                     MessageType type,
                                     String content,
                                     String toolName,
                                     String toolCallId) {
        MessageEntity message = new MessageEntity();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setType(type);
        message.setContent(content == null ? "" : content);
        message.setToolName(toolName);
        message.setToolCallId(toolCallId);
        return message;
    }
}
