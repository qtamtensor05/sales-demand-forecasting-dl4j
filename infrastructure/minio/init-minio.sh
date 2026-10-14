#!/bin/sh
set -eu

mc alias set lake http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
for bucket in bronze silver gold artifacts benchmarks; do
  mc mb --ignore-existing "lake/$bucket"
done
for prefix in bronze/store-1 silver/store-1 gold/store-1 artifacts/models; do
  bucket="${prefix%%/*}"
  key="${prefix#*/}/.keep"
  printf '' | mc pipe "lake/$bucket/$key"
done

if [ -f /bootstrap/input/train.csv ]; then
  mc cp /bootstrap/input/train.csv lake/bronze/store-1/train.csv
  size="$(wc -c < /bootstrap/input/train.csv | tr -d ' ')"
  rows="$(wc -l < /bootstrap/input/train.csv | tr -d ' ')"
  ingested_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '{"source":"data/train.csv","storeId":1,"bytes":%s,"csvLinesIncludingHeader":%s,"ingestedAt":"%s","immutableSource":true}\n' \
    "$size" "$rows" "$ingested_at" | mc pipe lake/bronze/store-1/ingestion-metadata.json
fi

if [ -f /bootstrap/model/model.zip ]; then
  mc mirror --overwrite /bootstrap/model lake/artifacts/models/global-residual-ar/seed-123
fi

