# 00. Audit repository trước khi tích hợp Big Data

Ngày kiểm tra: **2026-10-13**. Phạm vi: Giai đoạn 0, không huấn luyện lại và không sửa artifact thực nghiệm.

## Môi trường và cấu trúc

- Nhánh kiểm tra: `feature/bigdata-docker-platform`; worktree sạch trước khi triển khai.
- Windows 11, Java Temurin 11.0.32.1, Maven 3.9.16.
- Docker CLI 29.8.2 và Compose 5.5.1 có mặt, context `desktop-linux`; Docker Desktop Linux engine chưa chạy (`dockerDesktopLinuxEngine` không tồn tại), nên chưa thể pull/build/start container.
- Project hiện là một Maven module. Source chính ở `src/main/java`, test ở `src/test/java`, dữ liệu cục bộ ở `data/`, artifact bị loại khỏi Git ở `output/`.

## Dependency và pipeline hiện hữu

`pom.xml` đặt Java release 11, DL4J/ND4J/DataVec 1.0.0-M2.1, backend mặc định cục bộ là CUDA 11.6 và JUnit Jupiter 5.10.3. Các lớp chính đã đối chiếu:

- đọc/kiểm tra chuỗi: `TimeSeriesAnalyzer`;
- feature và split thời gian: `SalesPreprocessor`;
- tập global Store 1: `SingleStoreGlobalDataset`;
- train/validation: `GlobalExperimentRunner`, `GlobalResidualLstmForecaster`;
- inference/evaluation checkpoint: `GlobalTestEvaluationRunner`;
- baseline: `WeeklyNaiveBaseline`, công thức `sales(t-7)`.

Feature causal giữ nguyên: `sales`, `sales_lag_7`, `rolling_mean_7`, `rolling_mean_14`, `day_of_week_sin`, `day_of_week_cos`; cộng one-hot 50 item thành 56 kênh. Input 30 ngày, H1-H7, scaler Min-Max riêng từng item chỉ fit Train 2013-2015. Validation 2016, Test 2017.

## Dữ liệu và artifact đã xác minh

- `data/train.csv`: 17.333.449 byte; manifest ghi SHA-256 `038f25690a65149c94f86ddd3deceda20c037a5cfd754cafdfc539a72992f2ed`.
- Run `output/runs/2026-10-13_025422_624_gpu/run.json`: trạng thái `SUCCESS`, Store 1, 50 item, 52.300 training windows theo baseline đã xác nhận, batch 256, hidden 32, Adam 0,001, MSE.
- Deployment package có `model.zip`, `item-scalers.csv`, `feature-schema.json`, `item-mapping.json`, `model-metadata.json`, `selection.json`.
- Seed triển khai là 123, best epoch 20. Test manifest ghi SHA-256 checkpoint seed 123 là `f9d2dee2d3fcfb31ed61e08cd7b9b5f92d8ca9a7f1b0a95e5447afa477c7a482`.
- Evaluation-only Test 2017 đã hoàn tất: 17.950 origins, 376.950 forecast points cho ba seed; `trainingPerformed=false`, `weightsUpdated=false`, hash trước/sau giống nhau.

Checkpoint đã được restore thành công bởi luồng evaluation trên máy hiện tại với `JCublasBackend`. Chưa xác minh restore bằng CPU backend trong container vì Docker engine đang tắt; đây là điều kiện bắt buộc trước khi nhận Forecast Service MVP.

## Quyết định tích hợp

Spark sẽ tạo Gold Parquet causal và Java tiếp tục tensorization để tái sử dụng đúng công thức/scaler/model hiện hữu. Không đưa Test 2017 vào chọn model. Không ghi đè `output/`, checkpoint hay scaler. Phase 1 chỉ cung cấp bootstrap health endpoint cho Forecast/API/Frontend; nghiệp vụ thật thuộc các phase 3-6 và không được coi là đã hoàn thành.

## Rủi ro/blocker

Docker Desktop cần được bật để xác minh image, healthcheck, hai Spark worker, volume persistence và CPU restore. Đường dẫn mount deployment hiện trỏ đúng run cục bộ; khi đóng gói bàn giao cần chuyển artifact sang vị trí cấu hình ổn định mà vẫn giữ hash.

