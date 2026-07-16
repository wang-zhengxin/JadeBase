ALTER TABLE document_chunks ALTER COLUMN content TEXT;
ALTER TABLE document_chunks ALTER COLUMN embedding TEXT;
ALTER TABLE conversation_messages ALTER COLUMN content TEXT;
ALTER TABLE message_sources ALTER COLUMN snippet TEXT;
