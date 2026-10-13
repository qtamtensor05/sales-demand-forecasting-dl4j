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
- `POST /api/pipelines/ingest` nhận multipart field `file` chứa CSV;
- `GET /api/pipelines/ingest/{id}` trả tiến trình và lỗi xử lý;
- `POST /api/pipelines/ingest/{id}/retry` thử lại run đang `FAILED`.

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

## Luồng nhập CSV tự động

Backend kiểm tra đúng header `date,store,item,sales`, file rỗng, ngày/số sai, giá trị âm và khóa
`date + store + item` trùng trong cùng file. Phạm vi hiện tại chỉ nhận Store 1 và item 1-50 để khớp
Dashboard/model hiện hành. SHA-256 của toàn bộ byte file là khóa idempotency; upload
lại cùng nội dung trả run cũ và không gọi Spark lần nữa. File hợp lệ được lưu bất biến trong Bronze
theo `uploads/{checksum}/{fileName}`.

Worker một luồng chuyển trạng thái `ACCEPTED -> RUNNING`, gọi `spark-submit` cho
`IncrementalSalesEtlJob`, ghi Silver/Gold/statistics theo `run-{id}`. Chỉ khi Spark exit `0`, backend
mới upsert dữ liệu vào `sales_records` trong transaction và chuyển run sang `COMPLETED`; mọi lỗi được
ghi `FAILED` và có thể retry. Luồng này không gọi training và không thay model/scaler/checkpoint.

Từ migration V3, mỗi run còn có timeline bền vững theo attempt: `UPLOAD`, `VALIDATE`, `STORE_BRONZE`,
`SPARK_READ`, `WRITE_SILVER_GOLD`, `PUBLISH_POSTGRESQL`, `REFRESH_DASHBOARD`. Spark phát marker
`INGESTION_PROGRESS` trên stdout sau các hành động thật; backend đọc stream trong lúc process đang chạy
và lưu stage, trạng thái, số dòng hoàn thành/tổng số dòng cùng thời điểm bắt đầu/kết thúc. Endpoint chi
tiết run trả thêm `currentStage`, `processedRows`, `stages`, `durationMillis`, `rawRows` và `goldRows`.

Smoke test runtime run `4` xử lý 4 bản ghi trong `examples/ingestion-sample.csv`: Bronze,
Silver/Gold Snappy Parquet và JSON statistics đều có `_SUCCESS`; pipeline lưu `COMPLETED`, raw/gold
đều bằng 4 và PostgreSQL tăng từ 91.300 lên 91.304 dòng. Upload lần hai trả `duplicate=true`, cùng ID
`4`, attempts vẫn bằng `1`.

Giới hạn: CSV được kiểm tra trong bộ nhớ và giới hạn multipart 50 MB; executor hiện tuần tự để tránh
hai lần publish đồng thời trên máy local. Nếu backend dừng đúng lúc job đang chạy, run có thể cần đánh
dấu lỗi/retry thủ công sau khi dịch vụ lên lại; chưa có cơ chế lease/recovery phân tán.

## API dự báo đồng thời 50 sản phẩm

`POST /api/forecasts/batch` nhận `{"originDate":"2017-06-30"}`. Backend gọi một lần tới
`/forecast/batch`, nơi DL4J gom đủ 50 mẫu vào cùng một lượt suy luận. Phản hồi có 50 phần tử theo
`itemId`, mỗi phần tử có đúng bảy ngày. Backend chỉ ghi PostgreSQL sau khi nhận đủ kết quả, tạo 50
`forecast_requests` cùng 350 `forecast_results` trong một transaction.

Kiểm tra runtime ngày 2026-10-14 hoàn thành trong 4,18 giây, suy luận DL4J mất 1.603,545 ms. Phản hồi
có đủ 50 sản phẩm, từ 2017-07-01 đến 2017-07-07; PostgreSQL tăng 13 lên 63 request và 91 lên 441
result, mọi request mới đều có đúng bảy result.
