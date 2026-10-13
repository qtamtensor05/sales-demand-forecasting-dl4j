# Nhật ký yêu cầu

Nhật ký đầy đủ của các yêu cầu 001–087 được bảo toàn tại
[legacy change log](../archive/legacy-docs/legacy-change-log.md).

## 088 - 2026-10-14 - Refactor cấu trúc tài liệu, output và script vận hành

- **Mục tiêu:** Phân loại docs theo chức năng, chuẩn hóa output cho run mới, tập trung cấu hình đường dẫn và cung cấp CLI PowerShell thống nhất mà không di chuyển artifact lịch sử đang dùng.
- **Lý do:** Cấu trúc phẳng, output dùng chung và nhiều entrypoint làm tăng nguy cơ nhầm smoke/full/evaluation, sai mount hoặc thất lạc bằng chứng thực nghiệm.
- **Thay đổi:** Thêm `ProjectPaths`, `scripts/ProjectPaths.ps1`, `project.ps1`, managed model manifest; route run mới theo loại; tham số hóa Compose; tái cấu trúc docs; thêm audit, mapping, results index, sơ đồ và refactor report.
- **File ảnh hưởng:** Java output/main/evaluation, PowerShell scripts, Compose/env, README, `docs/`, artifact manifest và path-policy test.
- **Bảo toàn:** 12 run lịch sử, dataset, checkpoint, scaler, deployment package và Docker volumes không bị di chuyển/xóa/sửa. Docker vẫn dùng fallback legacy.
- **TDD:** RED do thiếu `ProjectPaths`; GREEN focused 9/9 sau triển khai.
- **Kết quả kiểm tra:** Root Maven 65/65 PASS; Backend 14/14 PASS; Spark 3/3 PASS; frontend content check và production build PASS. CLI help/results, Compose config và toàn bộ liên kết Markdown PASS. Forecast Service live healthy, trả đủ H1–H7; SHA-256 model trước/sau cùng là `f9d2dee2d3fcfb31ed61e08cd7b9b5f92d8ca9a7f1b0a95e5447afa477c7a482`.
- **Giới hạn:** Không chạy full training, không promote model sang đường dẫn mới, không tự nhận benchmark Spark/browser QA là PASS.
- **Commit đề xuất:** `refactor: standardize project outputs docs and operations`
