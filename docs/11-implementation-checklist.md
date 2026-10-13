# Checklist triển khai hệ thống Big Data Sales Demand Forecasting

Cập nhật lần cuối: **2026-10-13**.

Quy ước:

- `[x]`: đã triển khai và có bằng chứng chạy thực tế;
- `[-]`: đã làm một phần, chưa đạt toàn bộ yêu cầu;
- `[ ]`: chưa triển khai hoặc chưa có bằng chứng chạy thực tế.

## Tổng quan theo giai đoạn

| Giai đoạn | Trạng thái | Bằng chứng hiện có | Việc còn lại |
| --- | --- | --- | --- |
| 0. Audit repository | `[-]` | Có `00-repository-audit.md`; đã đối chiếu Java 11, Maven, DL4J/ND4J, dataset, checkpoint, scaler, schema, hash và source preprocessing/inference | Chưa chứng minh checkpoint seed 123 được nạp bằng CPU bên trong Forecast Service Docker |
| 1. Docker infrastructure | `[x]` | Compose đã build/start; MinIO, PostgreSQL và ba bootstrap HTTP healthy; Spark Master nhận 2 Workers; network, volume, port, restart policy và resource limit đã có | Các container ứng dụng vẫn là bootstrap, chức năng thuộc các phase sau |
| 2. MinIO Data Lake | `[-]` | Đã tạo đủ bucket/prefix; Bronze có CSV nguyên trạng và metadata; artifacts có sáu file seed 123; MinIO giữ dữ liệu sau restart | Silver/Gold mới có prefix, chưa có Parquet hoặc dữ liệu đã xử lý |
| 3. Spark ETL | `[-]` | Cluster 1 Master + 2 Workers hoạt động; module `spark-etl` có quality gate và JUnit 1/1 PASS | Chưa có ingestion/validation/cleaning/feature/statistics/benchmark jobs; chưa chạy runtime Spark, parity test hay xuất Parquet |
| 4. DL4J Forecast Service | `[ ]` | Checkpoint được mount read-only; bootstrap `/health` trả `UP` | Chưa restore checkpoint CPU, scaler, inference H1-H7, Weekly Naive, metadata/error handling và forecast parity |
| 5. PostgreSQL + Spring Boot API | `[ ]` | PostgreSQL healthy và volume persistence đã được kiểm tra | Chưa có migrations, tám bảng nghiệp vụ, Spring Boot endpoints, DTO, validation, OpenAPI, pagination, timeout hoặc idempotency |
| 6. React Dashboard | `[ ]` | Nginx bootstrap trả HTTP 200 | Chưa có React/TypeScript/Vite và năm trang nghiệp vụ; chưa lấy dữ liệu qua API |
| 7. Integration và kiểm thử | `[-]` | Compose smoke test, persistence probe và một focused ETL unit test đã PASS | Full root `mvn -B test` hiện đang chờ tải CUDA; chưa có Spark parity, checkpoint/service, API, migration và E2E tests |
| 8. Benchmark Big Data | `[ ]` | Chưa có benchmark runtime | Cần chạy lặp 1 Worker/2 Workers, cùng input/logic/resource policy và lưu số đo thật |
| 9. Tài liệu bàn giao | `[-]` | Có README và tài liệu 00, 01, 02; có sơ đồ kiến trúc và bằng chứng Docker/Data Lake | Thiếu tài liệu 03-10, hướng dẫn deployment/backup/troubleshooting, testing, benchmark và demo hoàn chỉnh |

## Checklist thành phần đích

- [-] Data Lake MinIO: Bronze và artifact đã có; Silver/Gold chưa có dữ liệu xử lý.
- [x] Spark Standalone: một Master và hai Workers đã đăng ký `ALIVE`.
- [ ] Spark ETL đầy đủ cho Store 1.
- [ ] Forecast Service DL4J thực hiện inference thật.
- [ ] Spring Boot REST API nghiệp vụ.
- [-] PostgreSQL: hạ tầng/persistence có, schema nghiệp vụ chưa có.
- [ ] React + TypeScript dashboard.
- [x] Docker Compose khởi động toàn bộ topology nền tảng.
- [-] Bộ test: mới có smoke/persistence/quality-rule test; các lớp kiểm thử bắt buộc khác còn thiếu.
- [ ] Benchmark 1 Worker so với 2 Workers.
- [-] Bộ tài liệu bàn giao: đã có phần nền tảng, chưa đủ tài liệu 00-10.

## Mười hai tiêu chí nghiệm thu

| # | Tiêu chí | Trạng thái | Kết luận hiện tại |
| ---: | --- | :---: | --- |
| 1 | Docker Compose khởi động thành công | `[x]` | `docker compose up --build -d` exit 0; các service dài hạn đang chạy |
| 2 | Spark Master nhận 2 Workers | `[x]` | Hai worker được Spark Master ghi nhận `ALIVE` |
| 3 | MinIO có đủ Bronze/Silver/Gold | `[-]` | Đủ bucket/prefix; chỉ Bronze có dữ liệu thật, Silver/Gold chưa có Parquet |
| 4 | Pipeline xử lý đúng 91.300 bản ghi | `[ ]` | Chỉ có constant/quality rule; chưa có kết quả Spark runtime |
| 5 | Các split đúng số lượng | `[ ]` | Rule biết 54.750/18.300/18.250 nhưng chưa đo trên output ETL |
| 6 | Forecast Service load checkpoint | `[ ]` | File được mount nhưng service chưa restore checkpoint bằng CPU |
| 7 | Dự báo H1-H7 hoạt động | `[ ]` | Implementation gốc có artifact Test; service Docker chưa cung cấp dự báo |
| 8 | Frontend lấy dữ liệu qua API | `[ ]` | Frontend và backend hiện chỉ là bootstrap độc lập |
| 9 | Forecast không lệch bất thường so với model gốc | `[ ]` | Chưa có forecast parity test giữa service và implementation gốc |
| 10 | Restart container không mất dữ liệu | `[x]` | MinIO giữ CSV và PostgreSQL giữ probe row sau restart |
| 11 | Benchmark lưu số đo thực tế | `[ ]` | Chưa triển khai/chạy benchmark Spark 1 Worker và 2 Workers |
| 12 | Không làm hỏng training/evaluation cũ | `[-]` | Lịch sử gần nhất có full suite 61/61 PASS trước phần Docker; lượt `mvn -B test` hiện tại chưa hoàn tất do đang tải CUDA |

Hiện có **3/12 tiêu chí đạt**, **3/12 đạt một phần** và **6/12 chưa đạt**. Đây là phép đếm theo
bằng chứng chạy thực tế, không phải phần trăm khối lượng phát triển.

## Đường găng tiếp theo

1. Hoàn tất Spark runtime ETL: Bronze → Silver Parquet → Gold, đo đủ count và dừng khi sai.
2. Viết parity tests giữa Spark Gold và Java preprocessing hiện tại.
3. Thay bootstrap Forecast Service bằng DL4J CPU inference dùng đúng checkpoint/scaler seed 123.
4. Tạo migrations và Spring Boot API, sau đó nối React dashboard qua API.
5. Bổ sung integration/E2E tests, benchmark 1/2 Workers và tài liệu 03-10.

Không chạy lại training, không đổi model/scaler/checkpoint và không dùng Test 2017 để chọn mô hình.
