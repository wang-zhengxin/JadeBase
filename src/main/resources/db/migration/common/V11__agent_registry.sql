CREATE TABLE agents (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    system_prompt TEXT NOT NULL,
    knowledge_base_id UUID NOT NULL REFERENCES knowledge_bases(id),
    model_provider_id UUID REFERENCES model_providers(id),
    model_id VARCHAR(255),
    access_level VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    enabled BOOLEAN NOT NULL,
    think_mode BOOLEAN NOT NULL,
    max_iterations INTEGER NOT NULL,
    current_version INTEGER NOT NULL,
    created_by UUID NOT NULL REFERENCES app_users(id),
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE agent_versions (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    system_prompt TEXT NOT NULL,
    knowledge_base_id UUID NOT NULL REFERENCES knowledge_bases(id),
    model_provider_id UUID REFERENCES model_providers(id),
    model_id VARCHAR(255),
    access_level VARCHAR(24) NOT NULL,
    think_mode BOOLEAN NOT NULL,
    max_iterations INTEGER NOT NULL,
    published_by UUID NOT NULL REFERENCES app_users(id),
    published_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE agent_runs (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    agent_version INTEGER NOT NULL,
    user_id UUID NOT NULL REFERENCES app_users(id),
    conversation_id UUID,
    status VARCHAR(24) NOT NULL,
    question TEXT NOT NULL,
    answer TEXT,
    error_message VARCHAR(1000),
    duration_ms BIGINT NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX idx_agent_version_number ON agent_versions(agent_id, version);
CREATE INDEX idx_agent_updated ON agents(updated_at);
CREATE INDEX idx_agent_access ON agents(access_level, enabled, current_version);
CREATE INDEX idx_agent_run_agent_started ON agent_runs(agent_id, started_at);
CREATE INDEX idx_agent_run_user_started ON agent_runs(user_id, started_at);
