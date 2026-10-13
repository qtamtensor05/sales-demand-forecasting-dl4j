# 05. PostgreSQL và Spring Boot API

Backend dùng Spring Boot 2.7/Java 11, PostgreSQL 16 và Flyway Community; tất cả đều chạy local bằng
Docker Compose, không cần dịch vụ cloud hoặc giấy phép trả phí. Swagger UI nằm tại
`http://localhost:8084/swagger-ui.html`, OpenAPI JSON tại `http://localhost:8084/v3/api-docs`.

## Schema và dữ liệu

Migration `V1__create_sales_platform.sql` tạo `stores`, `items`, `sales_records`,
`data_ingestion_runs`, `pipeline_runs`, `model_versions`, `forecast_requests`, `forecast_results`,
`evaluation_metrics` và `benchmark_runs`. `baseline-version=0` cho phép nâng cấp volume phát triển cũ
đã có probe table mà không xóa dữ liệu. Khi `sales_records` rỗng, ứng dụng nhập Store 1 từ
`/opt/data/train.csv` theo batch và dùng khóa chính để không nhập trùng.

## API

- `GET /api/health`, `/api/stores`, `/api/items`;
- `GET /api/items/{itemId}/sales?from=&to=&page=&size=`;
- `POST /api/forecasts` với `{"itemId":1,"originDate":"2017-06-30"}`;
- `GET /api/forecasts/{forecastId}`;
- `GET /api/models`, `/api/metrics`, `/api/pipelines`, `/api/benchmarks`;
- `POST /api/pipelines/ingest` yêu cầu header `Idempotency-Key`.

Forecast client có connect/read timeout, gọi Forecast Service nội bộ và lưu request cùng bảy horizon
vào PostgreSQL. Frontend chỉ gọi `/api`; Nginx proxy sang backend, không truy cập MinIO trực tiếp.

## Bằng chứng 2026-10-13

Container backend healthy; Flyway có hai dòng history (baseline 0 và migration 1); PostgreSQL có
91.300 sales rows. API trả 50 items, OpenAPI HTTP 200, request dự báo Item 1 tại origin 2017-06-30
trả `COMPLETED` với 7 horizon và PostgreSQL có 7 result rows. Request item 99 trả HTTP 400. Hai lần
gửi cùng `Idempotency-Key=demo-ingest-001` trả cùng ID và chỉ tạo một row.

Giới hạn: endpoint ingest hiện ghi nhận yêu cầu `ACCEPTED`, chưa có worker orchestration tự gọi
Spark. Bảng metrics/benchmarks chỉ trả dữ liệu thật đã lưu và có thể rỗng; không sinh dữ liệu giả.

## API phân tích nhu cầu theo tháng

`GET /api/analytics/monthly-demand?year=2017&limit=5` tổng hợp doanh số của toàn bộ 50 sản phẩm theo
tháng, xếp hạng trong từng tháng và trả top N với `month`, `itemId`, `totalSales`,
`averageDailySales`, `peakDailySales`, `rank`. API chấp nhận năm 2013-2017 và limit 1-50; tham số ngoài
phạm vi trả HTTP 400. Truy vấn tổng hợp chạy trong PostgreSQL để tránh frontend phải tải 50 chuỗi ngày.
