# Audit repository ngày 2026-10-14

## Phạm vi kiểm kê

Đã kiểm tra README, AGENTS, docs, scripts, output, data, artifacts/models, Compose, Dockerfiles, Maven
modules, Java core, Spark ETL, Backend API, Forecast Service, Frontend, cấu hình môi trường và GitHub.

| Khu vực | Hiện trạng trước refactor | Quyết định |
| --- | --- | --- |
| `docs/` | 16 Markdown phẳng, trộn hướng dẫn/kết quả/lịch sử | Phân nhóm 01–10 và project management |
| `output/` | 652 file, khoảng 211,7 MB; nhiều log ở root | Giữ lịch sử; chuẩn hóa run mới theo loại |
| `output/runs/` | 12 run lịch sử, chứa checkpoint/kết quả thực nghiệm | Không di chuyển hoặc sửa |
| `data/` | 4 file local, khoảng 18,7 MB | Không di chuyển; cấu hình qua `DATA_DIR` |
| `models/` | Không tồn tại ở root | Không tạo bản sao giả |
| `artifacts/` | Chưa có managed local artifact | Thêm manifest trỏ tới package active |
| Compose | Hai bind mounts hardcode deployment run | Dùng `MODEL_ARTIFACT_DIR`, giữ fallback cũ |
| Java | `Main` hardcode `data/train.csv`, `output/runs` | Thêm `ProjectPaths` |
| PowerShell | Mỗi script tự hardcode path/log | Thêm `scripts/ProjectPaths.ps1` |
| Vận hành | Nhiều Maven/Docker/script command | Thêm dispatcher `project.ps1` |

## Artifact phải bảo toàn tuyệt đối

- `output/runs/2026-10-13_025422_624_gpu/deployment/`;
- checkpoint từng seed và metadata trong cùng run;
- scaler, mapping, schema, selection và kết quả validation/Test lịch sử;
- toàn bộ `data/`, `output/runs/`, MinIO/PostgreSQL named volumes.

Model active SHA-256 trước refactor:
`f9d2dee2d3fcfb31ed61e08cd7b9b5f92d8ca9a7f1b0a95e5447afa477c7a482`.

## Phụ thuộc đường dẫn

- Compose `minio-init` và `forecast-service` cùng mount deployment package.
- Forecast Service và Backend mount `data/` vào `/opt/data`.
- Scripts training đọc `latest.txt` để nối calibration với full run.
- Evaluation đọc source run, checkpoint từng seed, scaler và epoch history.
- Tài liệu lịch sử chứa nhiều đường dẫn `output/runs`; đây là provenance, không phải đường dẫn mặc
  định cần viết lại.

Chi tiết migration tại [migration mapping](migration-mapping.md). Audit cũ được bảo toàn trong
[legacy repository audit](../archive/legacy-docs/legacy-repository-audit.md).
