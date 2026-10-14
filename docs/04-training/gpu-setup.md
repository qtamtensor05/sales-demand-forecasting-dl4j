# Chạy Full Global Experiment trên máy GPU mới

## Điều kiện

- JDK 11 và Maven 3.6.3+ trong `PATH`.
- NVIDIA driver/CUDA tương thích `nd4j-cuda-11.6-platform`, DL4J `1.0.0-M2.1` trong `pom.xml`.
- `data/train.csv` có Store 1, item 1..50, dữ liệu hằng ngày 2013-01-01..2017-12-31.

Kiểm tra thủ công: `java -version`, `mvn -version`, `nvidia-smi`.

## Runtime CUDA trên Windows

`nd4j-cuda-11.6-platform:1.0.0-M2.1` kéo được backend, JNI wrapper và classifier native ND4J,
nhưng `cuda-platform:11.6-8.3-1.5.7` chỉ kéo classifier `windows-x86_64` chứa JNI wrapper.
Project khai báo thêm `org.bytedeco:cuda:11.6-8.3-1.5.7:windows-x86_64-redist` ở runtime để
JavaCPP extract đúng CUDA 11.6/cuDNN 8.3 DLL. Artifact khoảng 1,8 GB trong Maven cache; lần chạy
đầu cần đủ dung lượng và thời gian tải/extract. Không cần cài system CUDA Toolkit 11.6 cho cấu hình này.

Trên máy RTX 3090 đã kiểm chứng, shell mặc định từng trỏ `java` tới JRE 8 và không có `mvn` trong
`PATH`. Trước khi chạy, bảo đảm `java -version` và `mvn -version` cùng báo Java 11; ví dụ cho phiên hiện tại:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-11.0.32.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;C:\Users\ezycloudx-admin\tools\apache-maven-3.9.16\bin;$env:Path"
```

Nếu policy máy chặn script, có thể chạy preflight bằng `powershell.exe -NoProfile
-ExecutionPolicy Bypass -File .\scripts\Invoke-GlobalTraining.ps1 -Preflight`; thao tác này chỉ
bypass cho process đó, không đổi policy toàn máy. Trong lúc chạy smoke, quan sát GPU bằng
`nvidia-smi -l 1`; không suy ra utilization từ việc backend chỉ load thành công.

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
Sau đó full chạy 3 fits với batch đã chọn 256/1024, tối đa 20 epoch/seed và patience 3.
Mốc 20 chỉ là hard cap; nếu validation Macro RMSE không cải thiện ba epoch liên tiếp thì fit dừng
sớm và khôi phục checkpoint tốt nhất. Script không cài
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

Ngày 2026-10-13, preflight trên Windows 10/RTX 3090 24 GB đã PASS với driver 616.56
(driver CUDA capability 13.4), Java 11.0.32.1, Maven 3.9.16 và ND4J CUDA build 11.6.55.
Backend chính xác là `org.nd4j.linalg.jcublas.JCublasBackend`; device 0 là RTX 3090, compute
capability 8.6. Đây không phải full training hoặc calibration và chưa đo utilization/peak VRAM.

Calibration một epoch đã đo 68,818477 giây cho candidate 256/1024 và chỉ dùng để chọn cấu hình
vận hành, không chứng minh chất lượng model cuối. Cap Full tăng từ 10 lên 20 để tránh chặn một fit
đang tiếp tục cải thiện ở epoch 10; validation vẫn chạy sau mỗi epoch và patience 3 giới hạn epoch
không hữu ích. Lệnh Full trực tiếp, chỉ chạy khi người dùng chủ động bắt đầu:

```powershell
.\scripts\Invoke-GlobalRun.ps1 -Mode Full -TrainBatchSize 256 -ValidationBatchSize 1024
```
