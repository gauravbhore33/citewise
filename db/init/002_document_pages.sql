CREATE TABLE document_pages (
    id           BIGSERIAL PRIMARY KEY,
    document_id  BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    page_number  INT NOT NULL,
    content      TEXT NOT NULL,
    method       VARCHAR(10) NOT NULL CHECK (method IN ('TEXT','OCR')),
    char_count   INT NOT NULL,
    UNIQUE (document_id, page_number)
);
CREATE INDEX idx_document_pages_document ON document_pages(document_id);