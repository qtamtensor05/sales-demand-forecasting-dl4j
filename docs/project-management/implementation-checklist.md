# Checklist triển khai và refactor

## Đã hoàn tất

- [x] README root rút gọn.
- [x] Docs phân nhóm 01–10 và project management.
- [x] Có Mermaid diagrams và results index.
- [x] Có repository audit, migration mapping và refactor report.
- [x] Java/PowerShell dùng policy đường dẫn tập trung.
- [x] Run mới tách training/evaluation/smoke/benchmark/log.
- [x] `output/runs` và artifact lịch sử không bị di chuyển.
- [x] Compose có configurable model mount với fallback legacy.
- [x] CLI thống nhất có help, validation, exit propagation và full-training guard.
- [x] Checkpoint SHA-256 không đổi.

## Chưa hoàn tất hoặc chưa xác minh

- [ ] Promote model active sang `artifacts/models` sau đầy đủ parity gate.
- [ ] Benchmark Spark lặp 1 worker/2 workers.
- [ ] Browser E2E/visual/keyboard QA.

Checklist nền tảng chi tiết trước refactor được lưu tại
[legacy checklist](../archive/legacy-docs/legacy-implementation-checklist.md).
