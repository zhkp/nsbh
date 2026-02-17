CREATE TABLE IF NOT EXISTS conversations (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    type VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    tool_name VARCHAR(128),
    tool_call_id VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations(id)
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_created
    ON messages(conversation_id, created_at);
