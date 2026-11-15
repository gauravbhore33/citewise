CREATE TABLE chat_messages (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question       TEXT NOT NULL,
    answer         TEXT,
    status         VARCHAR(20) NOT NULL CHECK (status IN ('ANSWERED','NOT_FOUND','ERROR')),
    reason         VARCHAR(30),
    top_similarity DOUBLE PRECISION,
    model          VARCHAR(100),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_messages_user ON chat_messages(user_id, created_at DESC);

CREATE TABLE chat_sources (
    id            BIGSERIAL PRIMARY KEY,
    message_id    BIGINT NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    n             INT NOT NULL,
    document_id   BIGINT REFERENCES documents(id) ON DELETE SET NULL,
    document_name VARCHAR(255) NOT NULL,
    page_number   INT NOT NULL,
    similarity    DOUBLE PRECISION NOT NULL,
    cited         BOOLEAN NOT NULL,
    content       TEXT NOT NULL
);
CREATE INDEX idx_chat_sources_message ON chat_sources(message_id);