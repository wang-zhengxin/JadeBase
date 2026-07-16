ALTER TABLE documents ADD COLUMN IF NOT EXISTS source_type VARCHAR(32);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS source_url VARCHAR(1000);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS source_author VARCHAR(255);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS source_updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS external_id VARCHAR(255);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS pending_reindex BOOLEAN DEFAULT FALSE NOT NULL;

CREATE TABLE IF NOT EXISTS feishu_connections (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    app_id VARCHAR(120) NOT NULL,
    encrypted_app_secret TEXT NOT NULL,
    api_base_url VARCHAR(500) NOT NULL,
    web_base_url VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL,
    status_message VARCHAR(500),
    last_connected_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS feishu_sources (
    id UUID PRIMARY KEY,
    connection_id UUID NOT NULL REFERENCES feishu_connections(id) ON DELETE CASCADE,
    knowledge_base_id UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    remote_id VARCHAR(255) NOT NULL,
    remote_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL,
    sync_interval_minutes INTEGER NOT NULL,
    last_sync_started_at TIMESTAMP WITH TIME ZONE,
    last_sync_completed_at TIMESTAMP WITH TIME ZONE,
    last_sync_status VARCHAR(32),
    last_sync_message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_feishu_source_remote
    ON feishu_sources(connection_id, source_type, remote_id, knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_feishu_source_schedule
    ON feishu_sources(enabled, last_sync_completed_at);

CREATE TABLE IF NOT EXISTS feishu_remote_documents (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES feishu_sources(id) ON DELETE CASCADE,
    document_id UUID NOT NULL,
    remote_token VARCHAR(255) NOT NULL,
    node_token VARCHAR(255),
    remote_parent_token VARCHAR(255),
    remote_type VARCHAR(32) NOT NULL,
    title VARCHAR(500) NOT NULL,
    author VARCHAR(255),
    source_url VARCHAR(1000),
    remote_revision VARCHAR(120),
    remote_updated_at TIMESTAMP WITH TIME ZONE,
    content_hash VARCHAR(64),
    last_seen_task_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_feishu_document_remote
    ON feishu_remote_documents(source_id, remote_token);
CREATE INDEX IF NOT EXISTS idx_feishu_document_seen
    ON feishu_remote_documents(source_id, last_seen_task_id);
CREATE INDEX IF NOT EXISTS idx_feishu_document_local
    ON feishu_remote_documents(document_id);

CREATE TABLE IF NOT EXISTS feishu_sync_tasks (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES feishu_sources(id) ON DELETE CASCADE,
    mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    cursor_json TEXT,
    scanned_count INTEGER NOT NULL,
    created_count INTEGER NOT NULL,
    updated_count INTEGER NOT NULL,
    deleted_count INTEGER NOT NULL,
    skipped_count INTEGER NOT NULL,
    attempt_count INTEGER NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_until TIMESTAMP WITH TIME ZONE,
    worker_id VARCHAR(255),
    error_message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_feishu_sync_poll
    ON feishu_sync_tasks(status, available_at);
CREATE INDEX IF NOT EXISTS idx_feishu_sync_source
    ON feishu_sync_tasks(source_id, created_at);
