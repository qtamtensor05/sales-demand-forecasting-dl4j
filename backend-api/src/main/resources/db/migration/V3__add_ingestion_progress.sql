ALTER TABLE data_ingestion_runs
  ADD COLUMN current_stage VARCHAR(40) NOT NULL DEFAULT 'UPLOAD',
  ADD COLUMN processed_rows BIGINT NOT NULL DEFAULT 0;

CREATE TABLE ingestion_stage_events (
  id BIGSERIAL PRIMARY KEY,
  ingestion_run_id BIGINT NOT NULL REFERENCES data_ingestion_runs(id) ON DELETE CASCADE,
  attempt INTEGER NOT NULL,
  stage VARCHAR(40) NOT NULL,
  status VARCHAR(20) NOT NULL,
  completed_rows BIGINT NOT NULL DEFAULT 0,
  total_rows BIGINT NOT NULL DEFAULT 0,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at TIMESTAMPTZ,
  message TEXT,
  UNIQUE (ingestion_run_id, attempt, stage)
);

CREATE INDEX idx_ingestion_stage_run ON ingestion_stage_events(ingestion_run_id, attempt, id);
