# Chạy Full Global Experiment trên máy GPU mới

## Điều kiện

- JDK 11 và Maven 3.6.3+ trong `PATH`.
- NVIDIA driver/CUDA tương thích `nd4j-cuda-11.6-platform`, DL4J `1.0.0-M2.1` trong `pom.xml`.
- `data/train.csv` có Store 1, item 1..50, dữ liệu hằng ngày 2013-01-01..2017-12-31.

Kiểm tra thủ công: `java -version`, `mvn -version`, `nvidia-smi`.

## Preflight không training

```powershell
.\scripts\Invoke-GlobalTraining.ps1 -Preflight
```

Phải thấy `GLOBAL PREFLIGHT PASS`, Store 1, 50 items, seeds `[42,123,2026]`, 52.300 train
windows, 18.000 validation origins và 126.000 forecasts/seed. Không chạy full nếu backend báo CPU.

## Full training

```powershell
.\scripts\Invoke-GlobalTraining.ps1
```

Expected: 3 fits, tối đa 30 epoch/seed, patience 5, batch 64. Script không cài dependency và
không tự giảm batch. Log ở `output/global-full-*.log`, exit code ở file thêm `.exit`.

Run ở `output/runs/<run-id>/`. Thành công khi `.exit` là `0`, Maven có `BUILD SUCCESS`, và
`run.json` có `status: SUCCESS`. Checkpoint seed ở `models/seed-*/`; model thắng ở
`deployment/model.zip`; `deployment/selection.json` ghi seed, epoch, Macro RMSE và nguồn.
Metric nằm trong `global-item-seed-metrics.csv`, `global-epoch-history.csv`; runtime trong
`performance-profile.json`.

```powershell
.\scripts\Audit-GlobalRun.ps1
```

Audit yêu cầu đủ 150 item-seed rows, 378.000 validation predictions và deployment package.
Chưa có runtime cho GPU mới cho đến khi máy đó thực sự chạy.
