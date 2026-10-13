# Cấu hình đường dẫn và môi trường

Các biến dùng chung:

| Biến | Mặc định | Vai trò |
| --- | --- | --- |
| `PROJECT_ROOT` | working directory | Root Java runtime |
| `DATA_DIR` | `data` | Dataset local |
| `OUTPUT_DIR` | `output` | Tất cả kết quả sinh ra |
| `ARTIFACTS_DIR` | `artifacts` | Artifact được quản lý |
| `MODEL_ARTIFACT_DIR` | run deployment lịch sử đã xác minh | Bind mount model Docker |

Java đọc các biến qua `ProjectPaths`; PowerShell dùng `scripts/ProjectPaths.ps1`. Đường dẫn tương đối
được resolve từ project root. Container path vẫn cố định (`/opt/data`, `/opt/model`) và được ánh xạ
rõ trong `compose.yaml`.

Không đổi `MODEL_ARTIFACT_DIR` sang package mới trước khi kiểm tra load model, SHA-256 và inference
parity.
