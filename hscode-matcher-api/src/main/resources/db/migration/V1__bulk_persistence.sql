-- Bulk chapter classify persistence (Postgres / Supabase compatible)

CREATE TABLE bulk_run (
    id UUID PRIMARY KEY,
    pipeline_id TEXT NOT NULL,
    language VARCHAR(8) NOT NULL,
    tuning JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    item_count INTEGER,
    error_summary TEXT,
    expected_chunk_count INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE bulk_run_chunk (
    run_id UUID NOT NULL REFERENCES bulk_run (id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    item_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    PRIMARY KEY (run_id, chunk_index)
);

CREATE INDEX idx_bulk_run_chunk_run ON bulk_run_chunk (run_id);

CREATE TABLE bulk_run_item (
    id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES bulk_run (id) ON DELETE CASCADE,
    row_index INTEGER NOT NULL,
    quotation_id TEXT,
    result_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_bulk_run_item_run_row UNIQUE (run_id, row_index)
);

CREATE INDEX idx_bulk_run_item_run ON bulk_run_item (run_id);
