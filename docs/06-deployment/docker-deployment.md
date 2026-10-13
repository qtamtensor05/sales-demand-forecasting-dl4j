# Hướng dẫn chạy và triển khai

Tài liệu này tập trung vào vận hành hệ thống. Chi tiết kiến trúc nằm trong
[system overview](../02-architecture/system-overview.md); kết quả mô hình nằm trong
[global model results](../05-evaluation/global-model-results.md).

## 1. Thành phần được triển khai

`compose.yaml` khởi động MinIO, PostgreSQL, Spark Master, hai Spark Worker, Spark job runner,
Forecast Service, Spring Boot API và React/Nginx Dashboard trên cùng mạng Docker.

Forecast Service chạy suy luận DL4J bằng CPU. GPU chỉ thuộc luồng huấn luyện riêng và không cần cho
việc chạy Dashboard hằng ngày.

## 2. Chuẩn bị

Máy triển khai cần Docker Desktop ở chế độ Linux containers, WSL2, JDK 11 và Maven. Từ root dự án:

```powershell
Copy-Item .env.example .env
```

Thay ít nhất hai giá trị sau trong `.env`:

```dotenv
MINIO_ROOT_PASSWORD=<mat-khau-rieng>
POSTGRES_PASSWORD=<mat-khau-rieng>
```

Không dùng mật khẩu mẫu trên máy dùng chung hoặc môi trường public.

Hai nguồn bắt buộc trước lần khởi động đầu:

- `data/train.csv`: dữ liệu lịch sử `date,store,item,sales`;
- `output/runs/2026-10-13_025422_624_gpu/deployment/`: model, metadata, scaler, mapping và schema.

Nếu triển khai run khác, sửa volume `/bootstrap/model` của service `minio-init` trong `compose.yaml`
để trỏ tới đúng thư mục `deployment/` của run đó.

## 3. Build và khởi động

```powershell
mvn -B -Pcpu-service package
docker compose config --quiet
docker compose up --build -d
docker compose ps
```

| Dịch vụ | Cổng | Mục đích |
| --- | ---: | --- |
| Dashboard | 3000 | Giao diện người dùng |
| Forecast Service | 8083 | Suy luận model nội bộ |
| Backend API | 8084 | API cho Dashboard |
| Spark Master UI | 8080 | Trạng thái cluster Spark |
| Spark Worker UI | 8081, 8082 | Trạng thái từng worker |
| MinIO API / Console | 9000 / 9001 | Data Lake và giao diện quản trị |
| PostgreSQL | 5432 | Kho dữ liệu nghiệp vụ |

Dashboard chỉ gọi `/api` qua Nginx; trình duyệt không cần truy cập trực tiếp MinIO hoặc Forecast
Service.

## 4. Luồng sử dụng

1. Mở <http://localhost:3000>.
2. Tải CSV đúng bốn cột `date,store,item,sales`.
3. Theo dõi các bước tiếp nhận, kiểm tra, lưu Bronze, Spark ETL và publish PostgreSQL.
4. Mở phần Phân tích để xem nhu cầu lịch sử.
5. Mở phần Dự báo, chọn ngày bắt đầu và phạm vi một hoặc toàn bộ 50 sản phẩm.

Upload cập nhật dữ liệu phân tích; nó không tự huấn luyện lại hoặc tự thay checkpoint đang phục vụ.

## 5. Vận hành container

```powershell
docker compose stop
docker compose start
docker compose down
```

`docker compose down` giữ named volume. Không dùng `docker compose down -v` nếu chưa chủ động sao lưu
và chấp nhận xóa dữ liệu MinIO, PostgreSQL.

Build lại các image ứng dụng sau khi source thay đổi:

```powershell
docker compose up --build -d forecast-service backend-api frontend
```

## 6. Chạy Java không qua Docker

```powershell
mvn compile exec:java
```

Pipeline đọc `data/train.csv` và ghi artifact vào `output/runs/<run-id>/`.

## 7. Huấn luyện và đưa model mới vào hệ thống

Máy huấn luyện cần GPU NVIDIA, driver phù hợp và CUDA Toolkit 11.6.

```powershell
.\scripts\Invoke-GlobalTraining.ps1 -Preflight
.\scripts\Invoke-GlobalTraining.ps1
```

Khi đưa model mới vào hệ thống:

1. giữ nguyên cả bộ file trong `deployment/`;
2. cập nhật đường dẫn model trong `compose.yaml`;
3. build lại JAR CPU và các container liên quan;
4. khởi động lại stack và kiểm tra trạng thái dịch vụ.

Có thể kiểm tra tính đầy đủ của full run bằng:

```powershell
.\scripts\Audit-GlobalRun.ps1 -RunDirectory output/runs/<run-id>
```

## 8. Kết quả và hồ sơ thực nghiệm

- [Global model một cửa hàng, nhiều sản phẩm](../05-evaluation/global-model-results.md)
- [Residual AR](../05-evaluation/residual-experiment.md)
- [ResidualAR MSE và Huber](../05-evaluation/residual-huber-experiment.md)
- [Trạng thái triển khai hiện tại](../project-management/current-state.md)
- [Lịch sử triển khai](../project-management/implementation-history.md)

Các số liệu trong những tài liệu này gắn với dataset, cấu hình và run-id cụ thể; không mặc định đại
diện cho dữ liệu khách hàng mới.
