# Phân loại kiểm thử

| Loại | Lệnh/điểm vào | Phụ thuộc | Thay đổi trạng thái |
| --- | --- | --- | --- |
| Unit | `.\project.ps1 test` | Maven; synthetic fixtures chủ yếu | Không |
| Smoke model | `.\project.ps1 smoke` | Dataset, backend DL4J | Tạo `output/smoke-tests/<run-id>` |
| Compose validation | `docker compose config --quiet` | Docker CLI | Không |
| Integration runtime | `.\project.ps1 status` và API probes | Docker stack | Chỉ đọc |
| Parity | các test preprocessing/inference chuyên biệt | Dataset/artifact tùy test | Không đổi checkpoint |
| E2E ingestion | Dashboard/API upload | Docker, MinIO, Spark, PostgreSQL | Có ghi dữ liệu runtime |
| Benchmark | `.\project.ps1 benchmark` | Dataset/GPU tương ứng | Tạo output benchmark |

Full training không phải smoke test. Browser visual/keyboard QA và benchmark Spark 1/2 worker chỉ
được báo PASS khi thực sự chạy.
