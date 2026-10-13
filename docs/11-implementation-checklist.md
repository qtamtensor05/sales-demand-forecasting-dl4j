# Checklist triển khai hệ thống Big Data Sales Demand Forecasting

Cập nhật lần cuối: **2026-10-13**.

Quy ước:

- `[x]`: đã triển khai và có bằng chứng chạy thực tế;
- `[-]`: đã làm một phần, chưa đạt toàn bộ yêu cầu;
- `[ ]`: chưa triển khai hoặc chưa có bằng chứng chạy thực tế.

## Tổng quan theo giai đoạn

| Giai đoạn | Trạng thái | Bằng chứng hiện có | Việc còn lại |
| --- | --- | --- | --- |
| 0. Audit repository | `[x]` | Có `00-repository-audit.md`; đã đối chiếu source/artifact và Forecast Service Docker đã load checkpoint seed 123 bằng CPU | Không còn blocker audit cho luồng MVP |
| 1. Docker infrastructure | `[x]` | Compose đã build/start; MinIO, PostgreSQL, Forecast, Spring Boot API và React/Nginx healthy; Spark Master nhận 2 Workers | Không còn bootstrap ứng dụng trong luồng MVP |
| 2. MinIO Data Lake | `[x]` | Bronze nguyên trạng; Silver và Gold Snappy Parquet; artifacts seed 123; benchmark stats; persistence đã kiểm tra | S3A committer hiện còn cảnh báo FileOutputCommitter |
| 3. Spark ETL | `[-]` | ETL batch và ingestion run-scoped chạy trên 2 workers; có Silver/Gold/statistics, trạng thái và retry | Chưa có benchmark lặp 1 Worker/2 Workers |
| 4. DL4J Forecast Service | `[x]` | CPU backend load đúng hash seed 123, health/metadata/forecast H1-H7, Weekly Naive, validation lỗi và parity 21 điểm PASS | Fat JAR đa nền tảng 1,45 GB cần tối ưu Linux-only |
| 5. PostgreSQL + Spring Boot API | `[x]` | Upload/validation/checksum/MinIO/Spark/status/retry và publish transaction chạy thật; run 4 tăng dữ liệu lên 91.304 rows | Chưa có recovery tự động nếu backend dừng giữa job |
| 6. React Dashboard | `[-]` | React/TypeScript/Vite có sáu màn hình; frontend gọi API và chạy forecast thật | Chưa browser QA; biểu đồ Actual/model/baseline và metric chi tiết còn cần hoàn thiện |
| 7. Integration và kiểm thử | `[-]` | Có unit, Compose/runtime migration, API/forecast HTTP end-to-end, Spark/forecast parity và root 64/64 | Chưa có automated PostgreSQL/API integration suite và browser E2E |
| 8. Benchmark Big Data | `[ ]` | Chưa có benchmark runtime | Cần chạy lặp 1 Worker/2 Workers, cùng input/logic/resource policy và lưu số đo thật |
| 9. Tài liệu bàn giao | `[-]` | Có README và tài liệu 00, 01, 02; có sơ đồ kiến trúc và bằng chứng Docker/Data Lake | Thiếu tài liệu 03-10, hướng dẫn deployment/backup/troubleshooting, testing, benchmark và demo hoàn chỉnh |

## Checklist thành phần đích

- [x] Data Lake MinIO: Bronze, Silver/Gold Parquet, artifact và runtime stats đã có.
- [x] Spark Standalone: một Master và hai Workers đã đăng ký `ALIVE`.
- [-] Spark ETL Store 1: batch và upload run-scoped, validation/Parquet/statistics/parity/retry đã có; benchmark còn thiếu.
- [x] Forecast Service DL4J thực hiện inference CPU thật.
- [x] Spring Boot REST API nghiệp vụ và OpenAPI.
- [x] PostgreSQL: Flyway schema, 91.304 sales rows sau smoke ingestion, model/pipeline/forecast persistence.
- [-] React + TypeScript dashboard: chạy thật và lấy dữ liệu qua API; còn browser QA/biểu đồ nâng cao.
- [x] Docker Compose khởi động toàn bộ topology nền tảng.
- [-] Bộ test: mới có smoke/persistence/quality-rule test; các lớp kiểm thử bắt buộc khác còn thiếu.
- [ ] Benchmark 1 Worker so với 2 Workers.
- [-] Bộ tài liệu bàn giao: đã có phần nền tảng, chưa đủ tài liệu 00-10.

## Mười hai tiêu chí nghiệm thu

| # | Tiêu chí | Trạng thái | Kết luận hiện tại |
| ---: | --- | :---: | --- |
| 1 | Docker Compose khởi động thành công | `[x]` | `docker compose up --build -d` exit 0; các service dài hạn đang chạy |
| 2 | Spark Master nhận 2 Workers | `[x]` | Hai worker được Spark Master ghi nhận `ALIVE` |
| 3 | MinIO có đủ Bronze/Silver/Gold | `[x]` | Cả ba layer có dữ liệu thật; Silver/Gold là Snappy Parquet |
| 4 | Pipeline xử lý đúng 91.300 bản ghi | `[x]` | Spark runtime báo raw và Gold đều 91.300 |
| 5 | Các split đúng số lượng | `[x]` | 54.750/18.300/18.250 và 52.300/18.000/17.950 đều qua fail-fast gate |
| 6 | Forecast Service load checkpoint | `[x]` | CPU backend load seed 123 với đúng SHA-256 deployment |
| 7 | Dự báo H1-H7 hoạt động | `[x]` | POST `/forecast` trả đủ bảy horizon và Weekly Naive |
| 8 | Frontend lấy dữ liệu qua API | `[x]` | React gọi `/api`; Nginx proxy tới Spring Boot, health và forecast H1-H7 end-to-end PASS |
| 9 | Forecast không lệch bất thường so với model gốc | `[x]` | 21 điểm: max CPU/GPU diff `5,07e-5` < `1e-3`; Weekly diff 0 |
| 10 | Restart container không mất dữ liệu | `[x]` | MinIO giữ CSV và PostgreSQL giữ probe row sau restart |
| 11 | Benchmark lưu số đo thực tế | `[ ]` | Chưa triển khai/chạy benchmark Spark 1 Worker và 2 Workers |
| 12 | Không làm hỏng training/evaluation cũ | `[x]` | Full suite sau thay đổi: 64/64 PASS, 0 failure/error/skip, `BUILD SUCCESS` trong 03:17 |

Hiện có **11/12 tiêu chí đạt**, **0/12 đạt một phần** và **1/12 chưa đạt**. Đây là phép đếm theo
bằng chứng chạy thực tế, không phải phần trăm khối lượng phát triển.

## Đường găng tiếp theo

1. Chạy benchmark lặp 1/2 Workers và lưu số đo thật.
2. Bổ sung automated API/migration và browser E2E tests; hoàn thiện biểu đồ nâng cao.
3. Hoàn thiện tài liệu 07-10, deployment/backup/troubleshooting và demo.

Không chạy lại training, không đổi model/scaler/checkpoint và không dùng Test 2017 để chọn mô hình.
