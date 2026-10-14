CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE data_ingestion_runs
  ADD COLUMN checksum_sha256 CHAR(64),
  ADD COLUMN original_filename VARCHAR(255),
  ADD COLUMN error_message TEXT,
  ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

UPDATE data_ingestion_runs
SET checksum_sha256 = encode(digest(idempotency_key, 'sha256'), 'hex')
WHERE checksum_sha256 IS NULL;

ALTER TABLE data_ingestion_runs ALTER COLUMN checksum_sha256 SET NOT NULL;
CREATE UNIQUE INDEX uq_data_ingestion_checksum ON data_ingestion_runs(checksum_sha256);
CREATE INDEX idx_data_ingestion_status ON data_ingestion_runs(status, started_at DESC);

ALTER TABLE pipeline_runs ADD COLUMN error_message TEXT;
