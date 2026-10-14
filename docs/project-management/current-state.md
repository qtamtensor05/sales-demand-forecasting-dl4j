# Trạng thái hiện tại

Cập nhật: **2026-10-14**.

## Hệ thống

Repository gồm DL4J GlobalResidualAR, Spark Standalone, MinIO, PostgreSQL, Spring Boot API,
Forecast Service CPU và React Dashboard. Model active vẫn là seed 123 được chọn bằng Validation 2016;
Test 2017 không dùng cho selection.

## Cấu trúc run

- full training mới: `output/training/<run-id>/`;
- evaluation mới: `output/evaluation/<source-run>_test-2017/`;
- smoke mới: `output/smoke-tests/<run-id>/`;
- benchmark/calibration mới: `output/benchmarks/<type>/<run-id>/`;
- logs mới: `output/logs/<task>/`;
- lịch sử: `output/runs/<run-id>/`, giữ nguyên.

## Model phục vụ

Compose nhận `MODEL_ARTIFACT_DIR` nhưng fallback về
`output/runs/2026-10-13_025422_624_gpu/deployment`. Không có checkpoint/scaler nào được di chuyển.
Managed index nằm tại `artifacts/manifests/current-model.json`.

## Vận hành

`project.ps1` là entrypoint cho setup, Compose, status/log, ingestion, forecast, smoke, test,
training có xác nhận, evaluation, benchmark, results và safe clean.

## Giới hạn còn lại

- Chưa promote model sang `artifacts/models`; cần model loading, hash và inference parity trước.
- Không chạy lại full training trong refactor.
- Benchmark Spark 1/2 worker và browser visual QA chưa được xác minh trong yêu cầu này.
- Kết quả thực nghiệm chi tiết nằm trong [results index](../10-results/results-index.md).

Trạng thái lịch sử đầy đủ được lưu tại
[legacy current state](../archive/legacy-docs/legacy-current-state.md).
