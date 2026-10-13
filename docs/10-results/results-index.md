# Chỉ mục kết quả

Tài liệu này chỉ trỏ tới bằng chứng thực tế, không sao chép log/checkpoint vào `docs/`.

| Phạm vi | Hồ sơ | Artifact thực tế |
| --- | --- | --- |
| GlobalResidualAR Store 1 / 50 items | [Global model results](../05-evaluation/global-model-results.md) | `output/runs/2026-10-13_025422_624_gpu/` |
| Test 2017 evaluation-only | [Evaluation protocol](../05-evaluation/evaluation-protocol.md) | legacy `test-evaluation/` trong source run hiện hữu; run mới dùng `output/evaluation/` |
| Residual AR | [Residual experiment](../05-evaluation/residual-experiment.md) | `output/runs/2026-10-12_092618_825_gpu/` |
| ResidualAR Huber | [Huber experiment](../05-evaluation/residual-huber-experiment.md) | `output/runs/2026-10-12_152631_034_gpu/` |
| Model đang phục vụ | [Managed manifest](../../artifacts/manifests/current-model.json) | legacy deployment package, không sao chép |

Run mới tra bằng `.\project.ps1 results`. Nếu artifact không có trên máy, ghi `NOT VERIFIED` thay vì
tạo dữ liệu thay thế.
