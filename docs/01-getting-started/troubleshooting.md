# Troubleshooting

- `Missing dataset`: đặt CSV tại `$DATA_DIR/train.csv` và kiểm tra header.
- `Missing deployment model`: kiểm tra `MODEL_ARTIFACT_DIR/model.zip` cùng năm file metadata đi kèm.
- Docker không khởi động: chạy `docker compose config --quiet`, sau đó `.\project.ps1 status`.
- Spark không nhận worker: mở <http://localhost:8080> và kiểm tra hai worker `ALIVE`.
- Forecast Service unhealthy: kiểm tra `.\project.ps1 logs -Service forecast-service`; không tự thay
  checkpoint hoặc scaler để né lỗi.
- Full training bị từ chối: đây là guard; dùng `-ConfirmFullRun` chỉ sau preflight và khi thật sự muốn
  chạy workflow dài.
