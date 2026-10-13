# 02. Data Lake và pipeline dữ liệu

## Trạng thái đã kiểm chứng ngày 2026-10-13

MinIO chạy trong Docker bằng image `bitnamilegacy/minio:2025.4.22-debian-12-r1`. Init job dùng
`bitnamilegacy/minio-client:2025.4.16-debian-12-r0`, tạo idempotent năm bucket:

- `bronze/store-1/`;
- `silver/store-1/`;
- `gold/store-1/`;
- `artifacts/models/`;
- `benchmarks/`.

Bronze hiện chứa bản sao nguyên trạng `data/train.csv` dung lượng 16 MiB và
`ingestion-metadata.json` ghi source, store, byte size, số dòng CSV gồm header, UTC ingest time và
`immutableSource=true`. Deployment package seed 123 được mirror read-only vào
`artifacts/models/global-residual-ar/seed-123/`, gồm model, scaler, mapping, schema, metadata và selection.

Sau khi restart MinIO, object `bronze/store-1/train.csv` vẫn tồn tại. PostgreSQL cũng giữ probe row sau
restart, xác nhận hai named volume hoạt động trong lượt kiểm tra này.

## Quality gate cho Spark ETL

Module `spark-etl` có quality rule fail-fast cho đúng Store 1: 91.300 dòng, 50 item, không duplicate,
không missing, Train 54.750, Validation 18.300 và Test 18.250. Focused JUnit đã đi từ RED
`UnsupportedOperationException` sang GREEN 1/1.

Các giá trị này mới là rule được kiểm thử, chưa phải kết quả Spark đã đo. Spark runtime job, Silver/Gold
Parquet, feature engineering, parity với Java và các count window/origin chưa hoàn thành; không được dùng
quality rule thay cho bằng chứng ETL thực.

