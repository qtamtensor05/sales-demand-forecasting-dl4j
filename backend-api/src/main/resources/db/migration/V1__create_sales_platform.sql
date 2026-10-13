CREATE TABLE stores (
  id INTEGER PRIMARY KEY,
  name VARCHAR(100) NOT NULL
);

CREATE TABLE items (
  id INTEGER PRIMARY KEY,
  store_id INTEGER NOT NULL REFERENCES stores(id),
  name VARCHAR(100) NOT NULL
);

CREATE TABLE sales_records (
  store_id INTEGER NOT NULL REFERENCES stores(id),
  item_id INTEGER NOT NULL REFERENCES items(id),
  sales_date DATE NOT NULL,
  sales INTEGER NOT NULL CHECK (sales >= 0),
  PRIMARY KEY (store_id, item_id, sales_date)
);

CREATE TABLE data_ingestion_runs (
  id BIGSERIAL PRIMARY KEY,
  idempotency_key VARCHAR(100) NOT NULL UNIQUE,
  status VARCHAR(30) NOT NULL,
  source_uri VARCHAR(500) NOT NULL,
  record_count BIGINT,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at TIMESTAMPTZ
);

CREATE TABLE pipeline_runs (
  id BIGSERIAL PRIMARY KEY,
  ingestion_run_id BIGINT REFERENCES data_ingestion_runs(id),
  status VARCHAR(30) NOT NULL,
  raw_rows BIGINT,
  gold_rows BIGINT,
  duration_ms BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE model_versions (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  seed INTEGER NOT NULL,
  checkpoint_sha256 CHAR(64) NOT NULL UNIQUE,
  status VARCHAR(30) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE forecast_requests (
  id UUID PRIMARY KEY,
  store_id INTEGER NOT NULL REFERENCES stores(id),
  item_id INTEGER NOT NULL REFERENCES items(id),
  origin_date DATE NOT NULL,
  model_version_id BIGINT REFERENCES model_versions(id),
  status VARCHAR(30) NOT NULL,
  inference_millis DOUBLE PRECISION,
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE forecast_results (
  request_id UUID NOT NULL REFERENCES forecast_requests(id) ON DELETE CASCADE,
  horizon INTEGER NOT NULL CHECK (horizon BETWEEN 1 AND 7),
  target_date DATE NOT NULL,
  prediction DOUBLE PRECISION NOT NULL,
  weekly_naive DOUBLE PRECISION NOT NULL,
  actual DOUBLE PRECISION,
  PRIMARY KEY (request_id, horizon)
);

CREATE TABLE evaluation_metrics (
  id BIGSERIAL PRIMARY KEY,
  model_version_id BIGINT REFERENCES model_versions(id),
  dataset_split VARCHAR(30) NOT NULL,
  metric_name VARCHAR(50) NOT NULL,
  metric_value DOUBLE PRECISION NOT NULL,
  scope VARCHAR(100) NOT NULL DEFAULT 'macro'
);

CREATE TABLE benchmark_runs (
  id BIGSERIAL PRIMARY KEY,
  worker_count INTEGER NOT NULL,
  input_rows BIGINT NOT NULL,
  duration_ms BIGINT NOT NULL,
  throughput_rows_second DOUBLE PRECISION NOT NULL,
  measured_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sales_item_date ON sales_records(item_id, sales_date);
CREATE INDEX idx_forecasts_created ON forecast_requests(created_at DESC);

INSERT INTO stores(id, name) VALUES (1, 'Store 1');
INSERT INTO items(id, store_id, name)
SELECT item_id, 1, 'Item ' || item_id FROM generate_series(1, 50) AS item_id;
INSERT INTO model_versions(name, seed, checkpoint_sha256, status)
VALUES ('GlobalResidualAR-MSE', 123, 'f9d2dee2d3fcfb31ed61e08cd7b9b5f92d8ca9a7f1b0a95e5447afa477c7a482', 'ACTIVE');
INSERT INTO data_ingestion_runs(idempotency_key, status, source_uri, record_count, finished_at)
VALUES ('store-1-initial', 'COMPLETED', 's3a://bronze/store-1/train.csv', 91300, now());
INSERT INTO pipeline_runs(ingestion_run_id, status, raw_rows, gold_rows, duration_ms)
SELECT id, 'COMPLETED', 91300, 91300, 64695 FROM data_ingestion_runs WHERE idempotency_key='store-1-initial';
