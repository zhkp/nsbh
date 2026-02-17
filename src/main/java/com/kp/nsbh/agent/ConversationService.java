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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    @Transactional
    public ConversationEntity createConversation() {
        return conversationRepository.save(new ConversationEntity());
    }

    @Transactional
    public ChatResult chat(UUID conversationId, String userMessage, String model) {
        ConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        String modelToUse = model == null || model.isBlank() ? properties.getLlm().getModelDefault() : model;

        messageRepository.save(newMessage(conversation, MessageRole.USER, MessageType.NORMAL, userMessage, null, null));
        maybeCompactMemory(conversation, modelToUse);

        List<MessageEntity> promptWindow = buildPromptWindow(conversationId);

        LlmReply firstReply;
        try {
            firstReply = llmClient.firstReply(userMessage, modelToUse, promptWindow);
        } catch (LlmClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
        List<ToolExecutionResult> toolCalls = new ArrayList<>();
        String assistantMessage = firstReply.assistantMessage();

        if (firstReply.toolCall() != null) {
            ToolCallRequest request = firstReply.toolCall();
            ToolExecutionResult toolResult = toolService.execute(
                    conversationId.toString(),
                    request.toolName(),
                    request.inputJson(),
                    request.id()
            );
            toolCalls.add(toolResult);

            messageRepository.save(newMessage(
                    conversation,
                    MessageRole.TOOL,
                    MessageType.NORMAL,
                    toolResult.result(),
                    toolResult.toolName(),
                    toolResult.toolCallId()
            ));

            promptWindow = buildPromptWindow(conversationId);
            try {
                assistantMessage = llmClient.finalReply(userMessage, modelToUse, toolResult.result(), promptWindow);
            } catch (LlmClientException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
            }
        }

        messageRepository.save(newMessage(conversation, MessageRole.ASSISTANT, MessageType.NORMAL, assistantMessage, null, null));
        return new ChatResult(assistantMessage, toolCalls);
    }

    @Transactional(readOnly = true)
    public List<MessageEntity> getMessages(UUID conversationId) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional(readOnly = true)
    public List<MessageEntity> getPromptWindow(UUID conversationId) {
        List<MessageEntity> all = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        int window = properties.getMemory().getWindow();
        if (all.size() <= window) {
            return all;
        }
        return all.subList(all.size() - window, all.size());
    }

    private List<MessageEntity> buildPromptWindow(UUID conversationId) {
        List<MessageEntity> prompt = new ArrayList<>();
        prompt.add(systemPromptMessage());

        MessageEntity summary = latestSummary(conversationId);
        if (summary != null) {
            prompt.add(summary);
        }

        List<MessageEntity> normals = messageRepository.findByConversationIdAndTypeOrderByCreatedAtAsc(
                conversationId,
                MessageType.NORMAL
        );
        int window = properties.getMemory().getWindow();
        if (normals.size() > window) {
            normals = normals.subList(normals.size() - window, normals.size());
        }
        prompt.addAll(normals);
        return prompt;
    }

    private void maybeCompactMemory(ConversationEntity conversation, String model) {
        UUID conversationId = conversation.getId();
        long normalCount = messageRepository.countByConversationIdAndType(conversationId, MessageType.NORMAL);
        if (normalCount <= properties.getMemory().getCompactAfter()) {
            return;
        }

        List<MessageEntity> normals = messageRepository.findByConversationIdAndTypeOrderByCreatedAtAsc(
                conversationId,
                MessageType.NORMAL
        );
        String summaryText;
        try {
            summaryText = llmClient.summarize(normals, model);
        } catch (LlmClientException e) {
            return;
        }

        MessageEntity summary = latestSummary(conversationId);
        if (summary == null) {
            messageRepository.save(newMessage(
                    conversation,
                    MessageRole.SYSTEM,
                    MessageType.SUMMARY,
                    summaryText,
                    null,
                    null
            ));
            return;
        }

        summary.setContent(summaryText);
        messageRepository.save(summary);
    }

    private MessageEntity latestSummary(UUID conversationId) {
        List<MessageEntity> summaries = messageRepository.findByConversationIdAndTypeOrderByCreatedAtDesc(
                conversationId,
                MessageType.SUMMARY
        );
        if (summaries.isEmpty()) {
            return null;
        }
        return summaries.get(0);
    }

    private MessageEntity systemPromptMessage() {
        MessageEntity message = new MessageEntity();
        message.setRole(MessageRole.SYSTEM);
        message.setType(MessageType.NORMAL);
        message.setContent(properties.getMemory().getSystemPrompt());
        return message;
    }

    private MessageEntity newMessage(ConversationEntity conversation,
                                     MessageRole role,
                                     MessageType type,
                                     String content,
                                     String toolName,
                                     String toolCallId) {
        MessageEntity message = new MessageEntity();
        message.setConversation(conversation);
        message.setRole(role);
        message.setType(type);
        message.setContent(content == null ? "" : content);
        message.setToolName(toolName);
        message.setToolCallId(toolCallId);
        return message;
    }
}
