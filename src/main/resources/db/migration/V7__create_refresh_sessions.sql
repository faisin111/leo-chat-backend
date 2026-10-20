CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    logout_at TIMESTAMPTZ,
    user_agent VARCHAR(500),
    ip_address VARCHAR(100)
);

CREATE INDEX idx_refresh_sessions_user ON refresh_sessions(user_id, created_at DESC);
