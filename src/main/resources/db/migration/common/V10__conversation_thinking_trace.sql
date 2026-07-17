ALTER TABLE conversation_messages ADD COLUMN reasoning_content TEXT;
ALTER TABLE conversation_messages ADD COLUMN thinking_duration_ms BIGINT NOT NULL DEFAULT 0;
ALTER TABLE conversation_messages ADD COLUMN thinking_steps INTEGER NOT NULL DEFAULT 0;
