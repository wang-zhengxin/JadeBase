CREATE TABLE IF NOT EXISTS knowledge_bases (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY,
    knowledge_base_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    chunk_count INTEGER NOT NULL,
    progress INTEGER NOT NULL,
    attempt_count INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS document_payloads (
    document_id UUID PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    content BYTEA NOT NULL
);

CREATE TABLE IF NOT EXISTS document_chunks (
    id UUID PRIMARY KEY,
    knowledge_base_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS conversations (
    id UUID PRIMARY KEY,
    knowledge_base_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS conversation_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    mode VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS message_sources (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    source_index INTEGER NOT NULL,
    document_id UUID NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    chunk_index INTEGER NOT NULL,
    snippet TEXT NOT NULL,
    score DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(255) NOT NULL,
    read BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS workspace_settings (
    id BIGINT PRIMARY KEY,
    profile_name VARCHAR(255),
    work_role VARCHAR(255),
    color_mode VARCHAR(32) NOT NULL,
    chat_background VARCHAR(32) NOT NULL,
    language VARCHAR(32) NOT NULL,
    topk INTEGER NOT NULL,
    show_citations BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_documents_knowledge_base ON documents(knowledge_base_id, created_at);
CREATE INDEX IF NOT EXISTS idx_chunks_knowledge_base ON document_chunks(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_chunks_document ON document_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_conversation_updated ON conversations(updated_at);
CREATE INDEX IF NOT EXISTS idx_message_conversation_created ON conversation_messages(conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_source_message ON message_sources(message_id, source_index);
