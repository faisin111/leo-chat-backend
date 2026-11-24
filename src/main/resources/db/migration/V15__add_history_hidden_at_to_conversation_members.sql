ALTER TABLE conversation_members ADD COLUMN IF NOT EXISTS history_hidden_at TIMESTAMPTZ;
