CREATE TABLE IF NOT EXISTS model_providers (
    id UUID PRIMARY KEY,
    provider_type VARCHAR(40) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    encrypted_api_key TEXT,
    status VARCHAR(32) NOT NULL,
    status_message VARCHAR(500),
    last_tested_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS language_models (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES model_providers(id) ON DELETE CASCADE,
    model_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL,
    default_model BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_language_model_provider_model
    ON language_models(provider_id, model_id);
CREATE INDEX IF NOT EXISTS idx_language_model_default
    ON language_models(default_model, enabled);
CREATE INDEX IF NOT EXISTS idx_model_provider_created
    ON model_providers(created_at);
