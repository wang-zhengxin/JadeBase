CREATE TABLE IF NOT EXISTS document_sets (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS document_set_items (
    id UUID PRIMARY KEY,
    document_set_id UUID NOT NULL REFERENCES document_sets(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_document_set_item_unique
    ON document_set_items(document_set_id, document_id);
CREATE INDEX IF NOT EXISTS idx_document_set_item_document
    ON document_set_items(document_id);

CREATE TABLE IF NOT EXISTS index_settings (
    id BIGINT PRIMARY KEY,
    chunk_size INTEGER NOT NULL,
    chunk_overlap INTEGER NOT NULL,
    candidate_k INTEGER NOT NULL,
    rrf_k INTEGER NOT NULL,
    rerank_enabled BOOLEAN NOT NULL,
    query_rewrite_enabled BOOLEAN NOT NULL,
    reindex_required BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
