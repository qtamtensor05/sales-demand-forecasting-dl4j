# Thí nghiệm Residual Autoregressive LSTM

Mục tiêu: kiểm chứng việc học phần điều chỉnh quanh Weekly Naive có cải thiện validation,
đặc biệt MEDIUM/HIGH, so với học sales tuyệt đối. Không kết hợp Huber, log1p, feature mới hoặc
kiến trúc mới trong thí nghiệm này. Full run đã hoàn tất; kết quả nghiên cứu cũ vẫn được giữ nguyên.

## Luồng đã kiểm tra và thiết kế

`SalesPreprocessor.createSplit` giữ raw target và target Min–Max trong mỗi window. Direct dùng
cả bảy target trong labels `[batch,7,30]`; AR dùng target đầu trong `[batch,1,30]`, mask chỉ bật
time step cuối. Weekly Naive lấy `rawInput[inputDays-7+h]`, với h=0..6. Scaler Min–Max fit từng
chuỗi trên train 2013–2015; validation 2016 chỉ transform và chọn epoch bằng RMSE sales.

`ResidualLstmForecaster` là facade riêng gọi cùng vòng fit/evaluate của AR với chế độ residual.
Giữ LSTM 32 units, TANH, output IDENTITY một kênh, MSE, Adam 0,001, full-batch, tối đa 30 epoch,
patience 5. Không copy toàn bộ model. API AR cũ tiếp tục mặc định target tuyệt đối.

```text
baseline(t) = observedSales(t-7)
residual(t) = actualSales(t) - baseline(t)
label(t) = residual(t) / trainSalesScale
finalPrediction(t) = baseline(t) + networkOutput(t) * trainSalesScale
```

Ví dụ `87−80=+7`, `90−100=−10`, `80−80=0`. `trainSalesScale` bằng max−min của sales train,
hoặc 1 cho chuỗi constant. Vì `transform(actual)−transform(baseline) = (actual−baseline)/scale`,
offset trainMin tự triệt tiêu. Không fit scaler residual riêng, không lấy min/max residual từ
validation/test, không cộng trainMin khi inverse residual. Độ lớn lỗi scaled dùng cùng mẫu số
như AR gốc, giúp phép so sánh chỉ thay target formulation. Không clip output âm.

AR vẫn chỉ học target H1 của mỗi window, giữ cùng số training samples như AR cũ; không thêm
cửa sổ gần cuối train. Validation loss là one-step residual MSE; checkpoint chọn theo **RMSE
rollout H1–H7 của finalPrediction ở sales gốc**, không chọn bằng residual loss.

## Bảo vệ protocol

- Với H1–H7, `t−7` luôn nằm trước hoặc tại origin. Baseline chỉ đọc input history và kiểm tra
  ngày anchor bằng targetDate−7; không đọc actual target để tạo input inference.
- Rollout đưa **finalPrediction** vào lịch sử sales rồi tái tạo lag/rolling/calendar như AR gốc.
  Actual target chỉ phục vụ tính metric. Không teacher forcing trong bảy bước rollout.
- Giữ toàn bộ preprocessing, chronology và 12 đại diện theo train-only tertile cùng năm seed
  `[42,123,2026,7,99]`. Smoke chọn chuỗi đầu trong chính danh sách đó, không đổi thuật toán chọn.
- Không đánh giá test 2017 hoặc dùng nó chọn model. Pipeline đọc CSV và dựng split test như trước;
  đây không phải tuyên bố rằng không đọc bất kỳ dòng CSV năm 2017 nào.
- Macro: mean MAE/RMSE từng seed trong từng chuỗi, sau đó mean đều giữa các chuỗi. Std ở bảng
  macro là sample std giữa series means; std theo seed được báo riêng. Win dùng RMSE thấp hơn
  nghiêm ngặt sau khi bình quân seed; không tính hòa là thắng.

## Giao diện chạy và artifact

Xem [training guide](../04-training/training-guide.md).
Lệnh cũ không tham số giữ 120 fits. `--residual` chạy 180 fits (12×5×3); `--residual-smoke`
chạy 3 fits (1×1×3), mỗi model tối đa 2 epoch. Full phải đối chiếu cả Weekly, Direct, AR và
ResidualAR trong cùng run; không ghép smoke với metric cũ để kết luận chất lượng.

`output/runs/<run-id>/` chứa:

| Artifact | Nội dung |
| --- | --- |
| `run.json` | Schema 2 cho residual, status, smoke, seeds, số fits, cấu hình, Java/backend, dataset SHA-256, target/scaling policy |
| `console.log` | Stdout/stderr ứng dụng; log Maven được lưu riêng bằng redirect/Tee-Object |
| `dataset-statistics.txt` | Thống kê dataset theo pipeline cũ |
| `epoch-history.csv` | Hai loss, RMSE sales, best flag; thêm model ResidualAR, giữ schema cũ |
| `seed-metrics.csv` | Giữ 12 cột cũ; thêm MAE/RMSE/best epoch/epochs run residual |
| `validation-predictions.csv` | Giữ 11 cột cũ; thêm predicted_residual ở sales gốc cho ResidualAR, để trống cho Direct/AR |
| `series-scaling.csv` | Stratum/store/item, train mean, sales min/max/scale và residual scale bằng sales scale |
| `summary.txt`, `error-analysis.txt`, `report.html` | Overall, LOW/MEDIUM/HIGH, H1–H7, seed, consistency và win rate so ba đối chứng |

Run cũ không bị sửa. Lệnh legacy giữ header CSV cũ; schema residual mở rộng bằng cột cuối,
không đổi nghĩa cột cũ. Công cụ đọc file residual cần đọc theo tên cột và chấp nhận model mới.
Thư mục được tạo độc quyền; nếu trùng timestamp thì thêm UUID, tránh ghi đè. Manifest chỉ chuyển
SUCCESS sau khi các artifact hoàn tất; Java exception hoặc đóng session chưa hoàn tất ghi FAILED.

## Tệp triển khai và kiểm thử

- Thêm [ResidualLstmForecaster](../../src/main/java/com/dl4j/salesforecast/model/ResidualLstmForecaster.java),
  [ExperimentConfiguration](../../src/main/java/com/dl4j/salesforecast/model/ExperimentConfiguration.java),
  [ResidualComparisonReport](../../src/main/java/com/dl4j/salesforecast/model/ResidualComparisonReport.java).
- Sửa [AutoregressiveLstmForecaster](../../src/main/java/com/dl4j/salesforecast/model/AutoregressiveLstmForecaster.java),
  [ExperimentRunner](../../src/main/java/com/dl4j/salesforecast/model/ExperimentRunner.java),
  [MultiSeriesExperimentRunner](../../src/main/java/com/dl4j/salesforecast/model/MultiSeriesExperimentRunner.java),
  [TrainingProgress](../../src/main/java/com/dl4j/salesforecast/model/TrainingProgress.java),
  [Main](../../src/main/java/com/dl4j/salesforecast/Main.java).
- Sửa [ValidationPredictionExporter](../../src/main/java/com/dl4j/salesforecast/model/ValidationPredictionExporter.java),
  [SeedMetricsExporter](../../src/main/java/com/dl4j/salesforecast/model/SeedMetricsExporter.java),
  [TrainingHistoryExporter](../../src/main/java/com/dl4j/salesforecast/model/TrainingHistoryExporter.java),
  [RunArtifactSession](../../src/main/java/com/dl4j/salesforecast/output/RunArtifactSession.java).
- Thêm [ResidualLstmForecasterTest](../../src/test/java/com/dl4j/salesforecast/model/ResidualLstmForecasterTest.java),
  [ResidualExperimentTest](../../src/test/java/com/dl4j/salesforecast/model/ResidualExperimentTest.java);
  mở rộng [RunArtifactSessionTest](../../src/test/java/com/dl4j/salesforecast/output/RunArtifactSessionTest.java).
- Cập nhật README, tài liệu này, current-state, implementation-history và change-log.

Các test JUnit 5 kiểm tra target dương/âm/zero; reconstruction; constant scale; dữ liệu validation/test
cực lớn không đổi scaler/train labels; target containment; input/labels/mask shapes; mask bước cuối;
forecast hữu hạn; horizon/date; zero output bằng Weekly; thay actual tương lai vẫn giữ dự báo cùng
origin; thay riêng test không đổi checkpoint; recompute MAE/RMSE; synthetic training; runner/export
ba model; macro hai tầng, sample std, win rate; manifest smoke/FAILED và tách thư mục run.

## Kết quả full experiment ngày 2026-10-12

Run `2026-10-12_092618_825_gpu` hoàn tất `SUCCESS` với 180 fits trên JCublasBackend trong
3 giờ 16 phút 03 giây. Dataset hash, 12 chuỗi, năm seed, split và cấu hình khớp protocol.

| Model | Macro MAE | Macro RMSE | RMSE so Weekly |
| --- | ---: | ---: | ---: |
| Weekly | 8,7365 ± 2,4252 | 11,1435 ± 3,1713 | 0,00% |
| Direct | 10,3217 ± 4,1082 | 13,0076 ± 5,1836 | -16,73% |
| AR | 9,7157 ± 3,5308 | 12,1396 ± 4,4385 | -8,94% |
| ResidualAR | 9,2169 ± 2,6440 | 11,6805 ± 3,3958 | -4,82% |

ResidualAR tốt hơn Direct/AR về macro và thắng mỗi model ở 8/12 chuỗi, nhưng thắng Weekly 0/12.
LOW/MEDIUM/HIGH có RMSE 8,1070/11,4804/15,4540, tương ứng kém Weekly 3,46%/5,20%/5,26%.
Residual thua AR ở 4/4 LOW nhưng thắng AR ở 4/4 MEDIUM và 4/4 HIGH. Target residual giải quyết
một phần vấn đề scale của MEDIUM/HIGH, chưa đủ vượt seasonal anchor.

Theo horizon, ResidualAR RMSE H1–H7 lần lượt 11,5741; 11,6071; 11,6476; 11,6842; 11,7076;
11,7450; 11,7889. Nó tốt hơn AR ở cả bảy horizon nhưng vẫn kém Weekly. Macro RMSE theo seed là
11,1603; 12,6071; 12,0819; 11,2796; 11,2735 cho seed 42/123/2026/7/99. Sample std 0,6358 thấp
hơn Direct 0,9213 và AR 0,9244, dù seed 123 cho thấy residual vẫn nhạy với khởi tạo.

Artifact được kiểm tra có 60 seed rows, 3.566 epoch rows, 180 best flags, 453.600 predictions;
metric tái tính khớp trong `5,1e-11`, reconstruction trong `1,1e-10`. Residual best epoch trung
bình 9,55, chạy trung bình 14,55 và dừng sớm 60/60 fits.

## Giới hạn nghiên cứu và vận hành

Kiểm chứng ngày 2026-10-12: **IMPLEMENTED / TESTED / SMOKE TESTED / FULL EXPERIMENT COMPLETED**.
Red test ban đầu: JUnit chạy được và assertion thất bại do thiếu class residual (1 failure,
không phải lỗi biên dịch). Focused model đạt 4/4; suite tích hợp đầu đạt 31/31; sau review bổ sung
ví dụ target và lifecycle, `mvn -B test` đạt **33/33**, 0 failures/errors/skips, exit 0,
BUILD SUCCESS trong 1:12 phút. Compiler giữ `release=11`; không nâng dependency.

Smoke thực tế: `mvn -B compile exec:java '-Dexec.args=--residual-smoke'`, exit 0, BUILD SUCCESS
38,900 giây. Run `2026-10-12_002237_861_gpu` ghi SUCCESS trên JCublasBackend, Java 11.0.32.1;
LOW store 7/item 34, seed 42, hai epoch mỗi model. Scaler train min=2/max=38/scale=36.
Đủ 10 artifact, 6 dòng epoch và 3 best flags, 1 dòng seed, 7.560 prediction (2.520 mỗi model,
360 mỗi horizon/model). Hash dataset khớp manifest; khóa prediction không trùng; reconstruction
sai lệch dưới `2e-10`; metric tính lại từ CSV khớp seed CSV trong `2e-9`.

Smoke MAE/RMSE: Weekly 5,3762/6,8709; Direct 17,2465/18,6808; AR 14,6671/16,1479;
ResidualAR 5,6356/7,1030. Cả ba neural model chọn epoch 2. Residual chưa vượt Weekly trong smoke,
nhưng một chuỗi/seed và hai epoch không đủ kết luận chất lượng hoặc hành vi MEDIUM/HIGH.
Log Maven nằm tại `output/residual-smoke-2026-10-12.log`; log suite tại
`output/residual-suite-final.log`, red tại `output/residual-tdd-red.log` (đều là artifact local).

Review implementation trước full run đã đối chiếu scaling/reconstruction, dữ liệu origin,
tensor/mask, checkpoint, horizon, macro, schema và trạng thái manifest. Full run sau đó được
kiểm tra lại từ artifact; không chạy training mới trong lượt cập nhật docs.

Đã có bằng chứng residual cải thiện so với AR tại MEDIUM/HIGH, nhưng Weekly Naive macro RMSE
11,1435 vẫn tốt nhất. Chỉ 12/500 chuỗi,
validation vừa chọn epoch vừa báo metric, cửa sổ chồng lấn; chưa bootstrap/CI và không có test mới.

CSV đầy đủ vẫn ghi cuối run, không lưu checkpoint hay resume. Nếu JVM bị kill cưỡng bức/native crash
hoặc mất điện trước khi Java xử lý lỗi, manifest có thể còn RUNNING; không công bố partial là SUCCESS.
Không thay dependency hoặc Java 11. Không commit/push.

Full residual đã đo 3:16:03, dài hơn ngoại suy tuyến tính 2:22:18 từ run 120 fits trước đó.
