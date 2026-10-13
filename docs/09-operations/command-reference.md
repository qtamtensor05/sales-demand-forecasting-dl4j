# Command reference

| Lệnh | Hành vi |
| --- | --- |
| `help` | Hiển thị giao diện CLI |
| `setup` | Kiểm tra prerequisite, `.env`, dataset và model |
| `up` / `down` | Khởi động / dừng Compose; `down` giữ volume |
| `status` | Compose status và HTTP health probes |
| `logs -Service <name>` | 200 dòng log gần nhất |
| `ingest -File <csv>` | Upload CSV qua Backend API |
| `forecast -Item <id> -OriginDate <date>` | Gọi inference một item |
| `smoke` | Global smoke, không phải full training |
| `test` | Maven test suite |
| `train -ConfirmFullRun` | Workflow training dài có guard |
| `evaluate -SourceRun <dir>` | Evaluation-only |
| `benchmark` | Global runtime benchmark |
| `results` | Hiển thị vị trí/latest của từng loại run |
| `clean` | Chỉ xóa isolated build snapshots |

Implementation scripts vẫn tồn tại dưới `scripts/`; `project.ps1` chỉ điều phối và chuyển tiếp lỗi.
