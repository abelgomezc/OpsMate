-- OpsMate initial schema
-- Incluye pgvector para embeddings (nomic-embed-text => 768 dimensiones).

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- cases: estado de un caso de remediacion (sobrevive reinicios del backend)
-- ---------------------------------------------------------------------------
CREATE TABLE cases (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    system_id       TEXT        NOT NULL,
    status          VARCHAR(32) NOT NULL,
    severity        VARCHAR(16),
    category        VARCHAR(64),
    priority        VARCHAR(16),
    description     TEXT,
    plan            TEXT,
    proposed_action TEXT,
    fail_reason     TEXT,
    timeout_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ,
    CONSTRAINT cases_status_check
        CHECK (status IN ('NEW', 'TRIAGING', 'INVESTIGATING', 'PLANNED', 'AWAITING_APPROVAL', 'EXECUTING', 'VERIFYING', 'RESOLVED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_cases_status ON cases (status);
CREATE INDEX idx_cases_system_id ON cases (system_id);
CREATE INDEX idx_cases_timeout_at ON cases (timeout_at) WHERE timeout_at IS NOT NULL;

-- ---------------------------------------------------------------------------
-- case_events: eventos append-only para trazabilidad y reconstruccion
-- ---------------------------------------------------------------------------
CREATE TABLE case_events (
    id         BIGSERIAL PRIMARY KEY,
    case_id    UUID        NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    type       VARCHAR(64) NOT NULL,
    payload    JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_case_events_case_id ON case_events (case_id, id);

-- ---------------------------------------------------------------------------
-- runbooks: procedimientos de remediacion con embedding para busqueda semantica
-- ---------------------------------------------------------------------------
CREATE TABLE runbooks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    description TEXT,
    steps       JSONB       NOT NULL,
    embedding   VECTOR(768),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indice de similitud coseno (IVFFlat) para busqueda por embedding.
CREATE INDEX idx_runbooks_embedding
    ON runbooks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- ---------------------------------------------------------------------------
-- past_cases: casos historicos resueltos, base para memoria/RAG
-- ---------------------------------------------------------------------------
CREATE TABLE past_cases (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       TEXT        NOT NULL,
    description TEXT,
    resolution  JSONB,
    embedding   VECTOR(768),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_past_cases_embedding
    ON past_cases USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- ---------------------------------------------------------------------------
-- approvals: aprobaciones registradas para acciones de riesgo HIGH
-- ---------------------------------------------------------------------------
CREATE TABLE approvals (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id      UUID        NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    action_type  TEXT        NOT NULL,
    requested_by TEXT        NOT NULL,
    approved     BOOLEAN,
    approved_by  TEXT,
    approved_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_approvals_case_id ON approvals (case_id);