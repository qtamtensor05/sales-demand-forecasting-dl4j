# Quản lý checkpoint

- Checkpoint trong run training là bằng chứng bất biến, không chỉnh sửa tại chỗ.
- Deployment package phải giữ model, metadata, scaler, mapping, schema và selection từ cùng run.
- `artifacts/manifests/current-model.json` chỉ mục model active; không chứa bản sao checkpoint.
- `MODEL_ARTIFACT_DIR` giữ đường dẫn legacy cho đến khi package mới qua kiểm tra load, hash và parity.
- Không dùng Test 2017 để thay đổi model active.

Audit full run:

```powershell
.\scripts\Audit-GlobalRun.ps1 -RunDirectory output/training/<run-id>
```
