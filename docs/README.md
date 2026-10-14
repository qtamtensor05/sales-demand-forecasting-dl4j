# Tài liệu dự án

Tài liệu được phân theo chức năng. `docs/` chỉ chứa Markdown, sơ đồ và chỉ mục; log, metrics,
checkpoint và kết quả chạy thực tế không được sao chép vào đây.

## Tra cứu nhanh

| Nhu cầu | Tài liệu |
| --- | --- |
| Cài đặt và chạy lần đầu | [Quick start](01-getting-started/quick-start.md) |
| Hiểu kiến trúc | [System overview](02-architecture/system-overview.md) và [sơ đồ](02-architecture/architecture-diagrams.md) |
| Hiểu dữ liệu/Data Lake | [Bronze–Silver–Gold](03-data/bronze-silver-gold.md) |
| Huấn luyện model | [Training guide](04-training/training-guide.md) |
| Xem protocol/kết quả | [Evaluation](05-evaluation/evaluation-protocol.md) và [results index](10-results/results-index.md) |
| Triển khai Docker | [Docker deployment](06-deployment/docker-deployment.md) |
| Chạy kiểm thử | [Testing overview](07-testing/testing-overview.md) |
| Chạy benchmark | [Benchmark methodology](08-benchmarks/benchmark-methodology.md) |
| Tìm lệnh vận hành | [Command reference](09-operations/command-reference.md) |
| Xem tiến độ/audit | [Project management](project-management/current-state.md) |

## Cấu trúc

- `01-getting-started/`: prerequisites, quick start, configuration, troubleshooting.
- `02-architecture/`: kiến trúc và Mermaid diagrams.
- `03-data/`: dataset, hợp đồng dữ liệu và Data Lake.
- `04-training/`: training, GPU, checkpoint và reproducibility.
- `05-evaluation/`: protocol và hồ sơ thực nghiệm.
- `06-deployment/`: Docker, Spark, MinIO, PostgreSQL, Forecast Service, API, Frontend.
- `07-testing/`: phạm vi và điều kiện của từng loại kiểm thử.
- `08-benchmarks/`: phương pháp benchmark; không chứa số liệu giả.
- `09-operations/`: CLI, run management, log và recovery.
- `10-results/`: chỉ mục trỏ tới artifact thực tế.
- `project-management/`: audit, migration, lịch sử, trạng thái và change log.

Output lịch sử vẫn ở `output/runs/`. Cấu trúc mới chỉ áp dụng cho run mới; xem
[migration mapping](project-management/migration-mapping.md).
