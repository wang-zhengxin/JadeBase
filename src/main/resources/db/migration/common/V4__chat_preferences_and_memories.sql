ALTER TABLE workspace_settings ADD COLUMN IF NOT EXISTS auto_scroll BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE workspace_settings ADD COLUMN IF NOT EXISTS smooth_streaming BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE workspace_settings ADD COLUMN IF NOT EXISTS collapse_large_pastes BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE workspace_settings ADD COLUMN IF NOT EXISTS personal_instructions VARCHAR(500) DEFAULT '' NOT NULL;
ALTER TABLE workspace_settings ADD COLUMN IF NOT EXISTS reference_memories BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE workspace_settings ADD COLUMN IF NOT EXISTS update_memories BOOLEAN DEFAULT FALSE NOT NULL;

CREATE TABLE IF NOT EXISTS workspace_memories (
    id UUID PRIMARY KEY,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_workspace_memory_content ON workspace_memories(content);
