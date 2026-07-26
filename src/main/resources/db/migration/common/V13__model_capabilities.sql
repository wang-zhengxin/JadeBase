ALTER TABLE language_models
    ADD COLUMN capability VARCHAR(32) DEFAULT 'CHAT' NOT NULL;

ALTER TABLE language_models
    ADD COLUMN embedding_dimensions INTEGER;

CREATE INDEX IF NOT EXISTS idx_language_model_capability_default
    ON language_models(capability, default_model, enabled);
