CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS embedding_vector vector(384);

UPDATE document_chunks
SET embedding_vector = ('[' || embedding || ']')::vector(384)
WHERE embedding_vector IS NULL AND embedding IS NOT NULL AND embedding <> ''
  AND array_length(string_to_array(embedding, ','), 1) = 384;

CREATE OR REPLACE FUNCTION jadebase_sync_embedding_vector()
RETURNS trigger AS $$
BEGIN
    NEW.embedding_vector := ('[' || NEW.embedding || ']')::vector(384);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_jadebase_sync_embedding_vector ON document_chunks;
CREATE TRIGGER trg_jadebase_sync_embedding_vector
BEFORE INSERT OR UPDATE OF embedding ON document_chunks
FOR EACH ROW EXECUTE FUNCTION jadebase_sync_embedding_vector();

CREATE INDEX IF NOT EXISTS idx_chunks_embedding_hnsw
ON document_chunks USING hnsw (embedding_vector vector_cosine_ops);
