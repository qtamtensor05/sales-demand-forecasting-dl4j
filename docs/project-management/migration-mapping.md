# Migration mapping

Migration ngày 2026-10-14 ưu tiên run mới và không di chuyển dữ liệu lớn hoặc kết quả lịch sử.

| Old path | New/default path | References cập nhật | Verification |
| --- | --- | --- | --- |
| `output/runs/<run-id>` | Giữ nguyên, compatibility zone | Audit script và results index vẫn đọc | 12 thư mục run lịch sử còn tại chỗ |
| full run mới trong `output/runs` | `output/training/<run-id>` | `ProjectPaths`, `Main`, scripts | `ProjectPathsTest` |
| smoke mới trong `output/runs` | `output/smoke-tests/<run-id>` | `ProjectPaths`, `Main`, CLI | `ProjectPathsTest` |
| benchmark/calibration trong `output/runs` | `output/benchmarks/<type>/<run-id>` | `ProjectPaths`, scripts | `ProjectPathsTest` |
| evaluation mới bên trong source run | `output/evaluation/<source-run>_test-2017` | evaluation main/script/runner | focused evaluation tests |
| root-level training logs | `output/logs/<task>/` | `Invoke-GlobalRun.ps1` | script/CLI checks |
| active deployment bind mount hardcode | `${MODEL_ARTIFACT_DIR}` với fallback legacy | `.env.example`, `compose.yaml` | Compose config |
| active deployment package | **Không di chuyển** | `artifacts/manifests/current-model.json` | SHA-256 trước/sau giống nhau |
| docs phẳng | `docs/01-...` đến `10-...`, `project-management/` | README và Markdown links | link checker |

## Compatibility rules

1. `output/runs/` không bị xóa, đổi tên hoặc ghi lại.
2. `Audit-GlobalRun.ps1` ưu tiên `output/training/latest.txt`, rồi fallback `output/runs/latest.txt`.
3. API Java cũ `GlobalTestEvaluationRunner.run(source,dataset)` vẫn ghi dưới source run; CLI mới dùng
   `output/evaluation/`.
4. Docker vẫn mount deployment package lịch sử. Chỉ đổi `MODEL_ARTIFACT_DIR` sau khi package mới qua
   model loading, SHA-256 và inference parity.
5. Không tự migrate Docker volumes hoặc MinIO prefixes.
