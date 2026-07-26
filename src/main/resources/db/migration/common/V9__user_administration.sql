ALTER TABLE app_users ADD COLUMN status VARCHAR(24) DEFAULT 'ACTIVE' NOT NULL;
ALTER TABLE app_users ADD COLUMN last_login_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_app_user_status ON app_users(status);

CREATE TABLE user_invitations (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    role VARCHAR(32) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    invited_by UUID NOT NULL REFERENCES app_users(id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX idx_user_invitation_token ON user_invitations(token_hash);
CREATE INDEX idx_user_invitation_email ON user_invitations(email);
CREATE INDEX idx_user_invitation_pending ON user_invitations(accepted_at, expires_at);

CREATE TABLE workspace_access_settings (
    id BIGINT PRIMARY KEY,
    restrict_open_signup BOOLEAN DEFAULT FALSE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO workspace_access_settings(id, restrict_open_signup, updated_at)
VALUES (1, FALSE, CURRENT_TIMESTAMP);
