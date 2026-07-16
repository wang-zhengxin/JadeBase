CREATE TABLE IF NOT EXISTS document_index_tasks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt INTEGER NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_until TIMESTAMP WITH TIME ZONE,
    worker_id VARCHAR(255),
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS document_chunk_terms (
    id UUID PRIMARY KEY,
    chunk_id UUID NOT NULL,
    document_id UUID NOT NULL,
    knowledge_base_id UUID NOT NULL,
    term VARCHAR(255) NOT NULL,
    term_frequency INTEGER NOT NULL,
    document_length INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_index_task_poll ON document_index_tasks(status, available_at);
CREATE INDEX IF NOT EXISTS idx_index_task_document ON document_index_tasks(document_id, created_at);
CREATE INDEX IF NOT EXISTS idx_chunk_term_lookup ON document_chunk_terms(knowledge_base_id, term);
CREATE INDEX IF NOT EXISTS idx_chunk_term_chunk ON document_chunk_terms(chunk_id);
CREATE INDEX IF NOT EXISTS idx_chunk_term_document ON document_chunk_terms(document_id);
