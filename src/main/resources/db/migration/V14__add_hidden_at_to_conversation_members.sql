ALTER TABLE conversation_members ADD COLUMN IF NOT EXISTS hidden_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_conversation_members_user_hidden
    ON conversation_members (user_id, hidden_at);
