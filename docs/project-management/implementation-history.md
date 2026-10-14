# Lịch sử triển khai

Tài liệu lịch sử đầy đủ trước refactor được bảo toàn tại
[legacy implementation history](../archive/legacy-docs/legacy-implementation-history.md).

## 53. Chuẩn hóa docs, output policy và CLI vận hành

**Mục tiêu:** phân tách tài liệu khỏi artifact runtime, chuẩn hóa nơi ghi run mới, giảm số lệnh cần
nhớ và bảo toàn checkpoint/kết quả lịch sử đang được Docker sử dụng.

**Triển khai:** chia docs theo chức năng; thêm `ProjectPaths` Java và helper PowerShell; route từng
loại run sang root riêng; tạo `project.ps1`; thêm managed model manifest; tham số hóa Docker mount
bằng `MODEL_ARTIFACT_DIR` với fallback legacy.

**An toàn migration:** không di chuyển 12 run lịch sử, dataset, deployment package hoặc Docker volume.
Hash model active được kiểm tra trước/sau. Audit script vẫn fallback đường dẫn cũ.

**Kết quả:** test path policy/evaluation/session tập trung đạt; CLI và Compose config chạy được.
Không thay model, scaler, train-only normalization hoặc chronological split.

**Commit đề xuất:** `refactor: standardize project outputs docs and operations`.
