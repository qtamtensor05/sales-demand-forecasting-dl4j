# Training guide

Run mới được ghi vào `output/training/<run-id>/`. Smoke và calibration không dùng chung thư mục với
full training.

```powershell
.\scripts\Invoke-GlobalTraining.ps1 -Preflight
.\project.ps1 smoke
.\project.ps1 train -ConfirmFullRun
```

Workflow full thực hiện preflight, smoke, calibration batch rồi training ba seed. Không chạy build
khác hoặc IDE auto-build lên runtime classes trong lúc training. Selection chỉ dùng Validation 2016;
Test 2017 không tham gia chọn seed, epoch hoặc hyperparameter.

Run thành công phải có manifest `SUCCESS`, checkpoint từng seed và deployment package nhất quán.
Xem [GPU setup](gpu-setup.md) và [checkpoint management](checkpoint-management.md).
