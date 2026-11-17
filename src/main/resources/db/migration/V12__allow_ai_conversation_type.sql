ALTER TABLE conversations DROP CONSTRAINT IF EXISTS conversations_type_check;

ALTER TABLE conversations
    ADD CONSTRAINT conversations_type_check CHECK (type IN ('DIRECT', 'GROUP', 'AI'));
