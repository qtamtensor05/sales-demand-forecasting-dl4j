# Evaluation protocol

- Train: 2013–2015.
- Validation: 2016, dùng cho checkpoint selection.
- Test: 2017, evaluation-only sau khi selection đã cố định.
- Scaler: fit riêng từng item trên train, không refit bằng validation/test.
- Baseline: Weekly Naive cùng horizon/key với model.

Chạy đánh giá checkpoint hiện có:

```powershell
.\project.ps1 evaluate -SourceRun output/training/<run-id>
```

Kết quả mới nằm tại `output/evaluation/<source-run>_test-2017/`. Runner xác minh dataset hash,
metadata, scaler, item coverage, checkpoint hash trước/sau và không cập nhật weights.
