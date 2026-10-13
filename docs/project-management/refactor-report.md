# Báo cáo refactor repository ngày 2026-10-14

## Trước và sau

Trước refactor, tài liệu nằm phẳng trong `docs/`, Java ghi mọi loại run vào `output/runs`, log/build
snapshot nằm trực tiếp dưới `output/`, Compose hardcode deployment run, và người dùng phải nhớ nhiều
lệnh Maven/PowerShell/Docker riêng.

Sau refactor:

- docs chia theo Getting Started, Architecture, Data, Training, Evaluation, Deployment, Testing,
  Benchmarks, Operations, Results và Project Management;
- run mới được phân loại theo mục đích;
- `ProjectPaths` và `scripts/ProjectPaths.ps1` tập trung hóa policy đường dẫn;
- `project.ps1` là entrypoint vận hành, còn script cũ giữ vai trò implementation;
- Compose dùng `MODEL_ARTIFACT_DIR` nhưng fallback nguyên đường dẫn deployment đã xác minh;
- artifact active được lập chỉ mục, không sao chép checkpoint vào docs hoặc Git.

## Dữ liệu và artifact được bảo toàn

- 12 run lịch sử và 652 file dưới `output/` không bị di chuyển hoặc chỉnh sửa.
- `data/` không bị di chuyển; Docker volumes không bị xóa hoặc recreate.
- Deployment `model.zip` vẫn ở đường dẫn cũ; SHA-256 trước và sau là
  `f9d2dee2d3fcfb31ed61e08cd7b9b5f92d8ca9a7f1b0a95e5447afa477c7a482`.

## Lệnh được rút gọn

`project.ps1` cung cấp `setup`, `up`, `down`, `status`, `logs`, `ingest`, `forecast`, `smoke`, `test`,
`train`, `evaluate`, `benchmark`, `results`, `clean`. Full training bắt buộc `-ConfirmFullRun`; clean
chỉ xóa isolated build snapshots sau khi kiểm tra target nằm dưới log root.

## Verification và giới hạn

- RED: focused test không compile vì `ProjectPaths` chưa tồn tại.
- GREEN: 9 focused tests đạt sau khi triển khai path policy.
- Full root Maven đạt 65/65; Backend đạt 14/14; Spark đạt 3/3; frontend test/build đạt.
- CLI `help`/`results`, Compose config và Markdown link checker chạy thành công.
- Forecast Service live healthy, trả đủ H1–H7; checkpoint SHA-256 trước/sau không đổi.
- Không chạy lại full training hoặc promote model sang đường dẫn mới.
- Benchmark Spark 1/2 worker và browser visual QA chỉ được coi là PASS khi thực sự chạy.
