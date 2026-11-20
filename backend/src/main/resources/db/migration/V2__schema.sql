-- OpsMate phase 6: fragmentos de runbooks (chunk-level) para busqueda semantica
CREATE TABLE runbook_chunks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source      TEXT        NOT NULL,
    chunk_text  TEXT        NOT NULL,
    chunk_index INT         NOT NULL,
    embedding   VECTOR(768),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_runbook_chunks_source ON runbook_chunks (source, chunk_index);
CREATE INDEX idx_runbook_chunks_embedding
    ON runbook_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
