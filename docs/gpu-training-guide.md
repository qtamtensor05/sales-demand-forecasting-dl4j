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

Một command tự chạy preflight, smoke GPU, rồi calibration ba cặp train/validation batch:
`64/256`, `128/512`, `256/1024`. Mỗi candidate dùng seed 42, toàn bộ 50 items và một epoch.
Ứng viên nhanh nhất trong ngưỡng Macro RMSE không tệ hơn baseline 64/256 quá 1% được chọn.
Sau đó full chạy 3 fits, tối đa 10 epoch/seed, patience 5 bằng cặp đã chọn. Script không cài
dependency hoặc âm thầm đổi batch khi OOM. Log từng phase ở `output/global-*.log`, exit code ở
file thêm `.exit`; quyết định calibration ở `output/global-calibration-selection-*.json`.

Run ở `output/runs/<run-id>/`. Thành công khi `.exit` là `0`, Maven có `BUILD SUCCESS`, và
`run.json` có `status: SUCCESS`. Checkpoint seed ở `models/seed-*/`; model thắng ở
`deployment/model.zip`; `deployment/selection.json` ghi seed, epoch, Macro RMSE và nguồn.
Metric nằm trong `global-item-seed-metrics.csv`, `global-epoch-history.csv`; runtime trong
`performance-profile.json`.
Run full còn có `calibration-selection.json` để nối model deployment với phép đo chọn batch.

```powershell
.\scripts\Audit-GlobalRun.ps1
```

Audit yêu cầu đủ 150 item-seed rows, 378.000 validation predictions và deployment package.
Chưa có runtime cho GPU mới cho đến khi máy đó thực sự chạy.
