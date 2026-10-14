# Quản lý run và log

```text
output/
  training/<run-id>/
  evaluation/<run-id>/
  smoke-tests/<run-id>/
  benchmarks/<benchmark-name>/<run-id>/
  logs/<task>/<run-id-or-log>/
  runs/<legacy-run-id>/
```

Mỗi run mới có ID riêng, manifest trạng thái và console log. `latest.txt` nằm trong root của từng loại,
không còn một con trỏ chung gây trộn smoke, benchmark và full training. Failed run được giữ để debug.

`output/runs/` không bị di chuyển hoặc xóa; đây là compatibility zone cho kết quả lịch sử.
