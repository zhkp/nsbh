package com.kp.nsbh.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class MessageEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 128)
    private String toolName;

    @Column(length = 128)
    private String toolCallId;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (type == null) {
            type = MessageType.NORMAL;
        }
    }

    public UUID getId() {
        return id;
    }

    public ConversationEntity getConversation() {
        return conversation;
    }

    public void setConversation(ConversationEntity conversation) {
        this.conversation = conversation;
    }

    public MessageRole getRole() {
        return role;
    }

    public void setRole(MessageRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
