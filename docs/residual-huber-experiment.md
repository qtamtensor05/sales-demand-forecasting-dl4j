# Thí nghiệm ResidualAR với Huber loss

## Kết quả full Huber ngày 2026-10-12

**IMPLEMENTED / TESTED / SMOKE TESTED / FULL EXPERIMENT COMPLETED.**
Run `2026-10-12_152631_034_gpu` ghi SUCCESS, JCublasBackend, Java 11.0.32.1,
12 chuỗi, 5 seed, 120 fits. Bắt đầu 15:26:31, kết thúc 16:27:11 giờ Việt Nam:
1 giờ 00 phút 40,79 giây, nhanh hơn ước lượng thô 2:10:42 trước đó.
Log `output/huber-full-2026-10-12_152619.log` xác nhận BUILD SUCCESS; exit code shell
không được lưu riêng nên không khẳng định đã kiểm tra giá trị đó.

So sánh dưới đây hoàn toàn trong cùng run, giữ delta=1 và validation-only selection.
Std là sample std giữa 12 series means sau bình quân seed.

| Model | MAE mean ± std | RMSE mean ± std | Cải thiện RMSE vs Weekly | vs MSE |
| --- | ---: | ---: | ---: | ---: |
| Weekly | 8,7365 ± 2,4252 | 11,1435 ± 3,1713 | 0% | +4,60% |
| ResidualAR-MSE | 9,216867 ± 2,643995 | 11,680474 ± 3,395809 | -4,82% | 0% |
| ResidualARHuber | 9,215712 ± 2,645803 | 11,680508 ± 3,395831 | -4,82% | -0,000288% |

Huber tăng RMSE khoảng 0,00003366; MAE giảm 0,001156 (khoảng 0,0125%).
Theo tiêu chí macro RMSE đây là **CASE C: không cải thiện ResidualAR**, với chênh lệch thực tế rất nhỏ.
Không có bằng chứng vượt Weekly hoặc lợi thế chất lượng có ý nghĩa thống kê.

| Stratum | Weekly RMSE | MSE RMSE | Huber RMSE | Huber thắng Weekly / MSE |
| --- | ---: | ---: | ---: | --- |
| LOW | 7,8359 | 8,107048 | 8,107057 | 0/4 / 1/4 |
| MEDIUM | 10,9134 | 11,480367 | 11,480410 | 0/4 / 0/4 |
| HIGH | 14,6811 | 15,454007 | 15,454057 | 0/4 / 0/4 |

Huber không cải thiện RMSE MEDIUM/HIGH. Toàn bộ 12 chuỗi: thắng Weekly 0/12,
thắng MSE 1/12 theo RMSE sau mean năm seed; strict wins không có ngưỡng ý nghĩa thực tiễn.

| Horizon | Weekly RMSE | MSE RMSE | Huber RMSE |
| --- | ---: | ---: | ---: |
| H1 | 11,1447 | 11,5741 | 11,5742 |
| H2 | 11,1461 | 11,6071 | 11,6070 |
| H3 | 11,1484 | 11,6476 | 11,6475 |
| H4 | 11,1431 | 11,6842 | 11,6841 |
| H5 | 11,1387 | 11,7076 | 11,7077 |
| H6 | 11,1420 | 11,7450 | 11,7452 |
| H7 | 11,1411 | 11,7889 | 11,7892 |

Huber nhỉnh hơn rất nhỏ tại H2–H4, kém tại H1/H5–H7; không vượt Weekly ở horizon nào.
Horizon xa không được cải thiện.

| Seed | MSE macro RMSE | Huber macro RMSE |
| --- | ---: | ---: |
| 42 | 11,160289 | 11,160275 |
| 123 | 12,607123 | 12,607236 |
| 2026 | 12,081942 | 12,081925 |
| 7 | 11,279557 | 11,279626 |
| 99 | 11,273461 | 11,273477 |

Sample std macro RMSE giữa seed đều khoảng 0,6358; min/max MSE 11,160289–12,607123,
Huber 11,160275–12,607236. Huber tốt hơn MSE rất nhỏ ở 2/5 seed; không seed nào vượt
macro Weekly. MAE seed std giảm 0,5577 xuống 0,5556, chưa cho thấy thay đổi đáng kể về ổn định.

### Kiểm chứng artifact và learning history

Đối chiếu trực tiếp manifest, summary, seed/epoch/prediction CSV và series-scaling:
60 seed rows, 1.747 epoch rows, 120 fits và đúng một best flag/fit; 302.400 predictions,
151.200/model, mỗi fit 2.520, mỗi horizon/fit 360. Khóa prediction duy nhất, mọi giá trị hữu hạn,
đủ H1–H7. Dataset SHA-256 khớp file hiện tại:
`038f25690a65149c94f86ddd3deceda20c037a5cfd754cafdfc539a72992f2ed`.
MAE/RMSE tái tính từ prediction khớp seed CSV trong `5,1e-11`;
reconstruction `baseline + predicted_residual` khớp trong `1,1e-10`.

MSE chạy 873 epoch, Huber 874; best epoch trung bình 9,55 và 9,5667.
Cả hai dừng sớm 60/60 fits; chỉ 1/60 cặp seed-series có best epoch khác.
Không chạy lại training/Maven test trong lượt cập nhật báo cáo này; 39 tests là bằng chứng
của lượt triển khai trước. Code Java, dependency, dữ liệu và artifact không bị sửa.

### Diễn giải và bước tiếp theo

Kết quả gần trùng phù hợp với giả thuyết delta=1 phần lớn ở vùng quadratic; tuy nhiên artifact
không lưu phân bố training error/tỷ lệ vượt delta, nên chưa chứng minh cơ chế đó.
Nhãn train nhỏ hơn 1 không đồng nghĩa mọi prediction error nhỏ hơn 1.
Huber chuẩn có hệ số 0,5 so MSE trong vùng quadratic; không so trực tiếp hai training loss để
kết luận model tốt hơn. Không tự đổi delta sau khi xem validation.

Giới hạn vẫn là 12/500 chuỗi, validation vừa chọn epoch vừa báo metric, windows chồng lấn,
chưa kiểm định/CI và không đánh giá test 2017 để selection. Bước kế tiếp chỉ đề xuất một
thí nghiệm riêng residual + variance stabilization/log1p với protocol định trước; chưa triển khai.


## Mục tiêu và đối chứng

Chỉ thay objective MSE bằng Huber trong ResidualAR để kiểm tra tác động lên validation 2016,
đặc biệt MEDIUM/HIGH và horizon xa. Hai model `ResidualAR` và `ResidualARHuber` train trong
cùng run; Weekly Naive dùng cùng windows. Không train lại Direct/AR, không ghép metric run cũ.

Full dùng 12 đại diện train-only, seed `[42,123,2026,7,99]`: **120 neural fits**. Giữ nguyên
30 input days, 7 horizons, 6 features, LSTM 32 units/TANH, output 1/IDENTITY, Adam 0,001,
full batch, tối đa 30 epoch, patience 5. Checkpoint vẫn chọn bằng RMSE rollout H1–H7 ở sales
gốc trên validation, không chọn bằng Huber loss. Không tính test 2017 để chọn model/delta/epoch.

```text
label = (actual(t) - observedSales(t-7)) / trainSalesScale
prediction = observedSales(t-7) + networkOutput * trainSalesScale
```

Scale vẫn là max−min của sales train 2013–2015, hoặc 1 nếu constant. Không fit residual scaler,
không cộng trainMin khi reconstruct, không clip, không thêm log1p/feature hoặc tuning khác.
Actual tương lai chỉ dùng chấm metric; rollout feed sales đã tái dựng vào causal features.

## API và delta

Đã kiểm tra JAR và source JAR `nd4j-api:1.0.0-M2.1` trong Maven cache: không có Huber trong
`lossfunctions.impl`. Vì vậy thêm `HuberLoss implements ILossFunction`, theo contract mask,
activation backprop, score averaging và RNN flattening của phiên bản hiện tại; không nâng dependency.
Phép toán tensor chạy trên backend ND4J hiện hữu, không chuyển training sang CPU.

`LossMode.MSE/HUBER` là điểm thay đổi duy nhất trong network builder. Facade residual dùng chung
vòng fit, labels, inference và checkpoint với AR hiện tại; overload cũ tiếp tục mặc định MSE.

Với `e = predictedScaledResidual − label`, delta cố định **1,0**:

```text
L(e) = 0.5*e²                  nếu |e| <= 1
L(e) = |e| - 0.5               nếu |e| > 1
dL/de = e                      nếu |e| <= 1
dL/de = sign(e)                 nếu |e| > 1
```

Không tune delta trên validation. Đây là Huber chuẩn: vùng quadratic bằng **một nửa MSE**,
gradient cũng bằng một nửa; không nhân đôi loss để giả lập MSE. Adam giữ nguyên learning rate.
Do đó không được quy mọi khác biệt cho việc giảm ảnh hưởng outlier.

Kiểm tra train-only trên đúng 12 đại diện: 12.552 nhãn H1 (1.046/chuỗi), |label| median
0,084112, P95 0,277778, P99 0,383178, max 0,6875; không nhãn nào vượt 1. Đây là thống kê nhãn,
không phải thống kê error trong lúc train. Delta 1 tương ứng một sales range train, nên đa số lỗi
có thể ở vùng quadratic khi output gần nhãn. Báo đây là giới hạn, không tự đổi delta.

## CLI và thời gian

Chạy từ root repository, giữ backend CUDA 11.6 hiện tại:

```powershell
Set-Location 'C:\Users\quangTam\Documents\DL4j\sales-demand-forecasting-dl4j'
Test-Path pom.xml
mvn -B compile exec:java '-Dexec.args=--residual-huber-smoke'
```

Smoke: chuỗi đầu của cùng 12 đại diện, seed 42, hai model, tối đa 2 epoch/model.
Full khi chủ động bắt đầu thí nghiệm dài:

```powershell
Set-Location 'C:\Users\quangTam\Documents\DL4j\sales-demand-forecasting-dl4j'
New-Item -ItemType Directory -Force output | Out-Null
$huberRunLog = 'output/residual-huber-full-' + (Get-Date -Format 'yyyy-MM-dd_HHmmss') + '.log'
mvn -B clean compile exec:java '-Dexec.args=--residual-huber' 2>&1 | Tee-Object -FilePath $huberRunLog
$huberExitCode = $LASTEXITCODE
```

120 fits, tối đa 3.600 epoch. Ngoại suy từ full residual 180 fits mất 3:16:03 cho khoảng **2:10:42**;
đây chỉ là ước lượng thô vì bỏ Direct và số epoch early stopping/chi phí loss khác nhau.
Không compile/test hoặc IDE auto-build vào target/classes khi train. Lưu stdout/stderr đầy đủ,
chờ kết thúc mới đọc log. Không chạy full tự động trong lượt triển khai/test/smoke này.

## Artifact và aggregation

Mỗi lần tạo `output/runs/<run-id>/`, không sửa run cũ. Schema **3** áp dụng riêng Huber comparison:

- `run.json`: loss=`MIXED`; `lossFunction` map `{ResidualAR:MSE, ResidualARHuber:HUBER}`;
  `huberDelta=1.0`, delta space/definition, checkpoint criterion, hash dataset và config thực tế.
- `epoch-history.csv`: `train_loss_scaled`, `val_loss_scaled`, `val_rmse_sales`, best flag,
  thêm `loss_function,huber_delta`; tuyệt đối không gắn nhãn MSE cho Huber.
- `seed-metrics.csv`: một dòng/series/seed; bốn metric/checkpoint fields cho residual MSE và
  bốn cho Huber. Không tạo metric rỗng/giả cho Direct/AR chưa train.
- `validation-predictions.csv`: giữ schema residual 12 cột, tên model phân biệt; residual lưu
  ở sales gốc, `prediction ≈ weekly_naive + predicted_residual`.
- `series-scaling.csv`, `console.log`, `dataset-statistics.txt`, `summary.txt`, `error-analysis.txt`,
  `report.html` tiếp tục được xuất. SUCCESS chỉ sau khi xuất đủ; thiếu coverage bị từ chối.

Legacy schema 1 và residual MSE schema 2 giữ header/ý nghĩa cũ; consumer cần chọn schema qua
manifest. Report lấy mean seed trong từng series rồi mean đều giữa series; std là sample std
giữa series means. Có overall, LOW/MEDIUM/HIGH, H1–H7, từng seed, sample std và min/max macro
RMSE giữa seed; improvement so Weekly và residual MSE, win nghiêm ngặt theo series seed mean.

## Tệp triển khai

Thêm `model/HuberLoss.java`, `model/LossMode.java`; mở rộng AR/residual forecaster,
ExperimentConfiguration/ExperimentRunner, MultiSeriesExperimentRunner, ResidualComparisonReport,
ba CSV exporter, Main và RunArtifactSession. Thêm ResidualHuberTest; mở rộng hai test residual.
Không sửa SalesPreprocessor, WeeklyNaiveBaseline, Direct forecaster, pom.xml hoặc artifact cũ.

## Kiểm chứng code

Kiểm chứng code ngày 2026-10-12: TDD RED có 2 assertion failures, 0 errors (thiếu class Huber
và CLI mới), log `output/huber-tdd-red.log`. Focused mở rộng đạt 16/16 trong 1:29 phút,
log `output/huber-focused-final.log`. Suite cuối `mvn -B test` đạt **39/39**, 0 failures/errors/
skips, exit 0, BUILD SUCCESS trong 1:31 phút; log `output/huber-suite-final.log`.

Test bao gồm loss/error âm/dương/zero và hai phía delta; gradient số, mask theo row/output,
activation backprop, score averaging, serialization cấu hình; synthetic train cả MSE/Huber,
reconstruction, mask/shape, train-only scaler/labels trước thay đổi future cực lớn, prediction
không dùng actual tương lai, checkpoint min validation RMSE và test-independent selection.
Có regression MSE overload cũ, config legacy/residual, export hai model, macro/win/std/minmax,
manifest SUCCESS và từ chối coverage sai rồi FAILED khi session đóng chưa hoàn tất.

Review đối chiếu Java 11, dependency hiện tại, delta semantics, tensor/mask, scaler, reconstruction,
rollout, checkpoint, schema, macro và lifecycle: chưa thấy lỗi chặn trong phạm vi đã kiểm tra.
Không đổi pom.xml, preprocessing hoặc Direct model. Ở thời điểm triển khai chưa chạy full;
kết quả full hoàn tất sau đó được bổ sung ở đầu tài liệu.

## Giới hạn

Trạng thái tại thời điểm smoke (lịch sử): **IMPLEMENTED / TESTED / SMOKE TESTED / FULL EXPERIMENT NOT YET RUN**.

Smoke `2026-10-12_143207_100_gpu` hoàn tất SUCCESS, Java 11.0.32.1, JCublasBackend.
Lệnh `mvn -B compile exec:java '-Dexec.args=--residual-huber-smoke'` exit 0/BUILD SUCCESS,
38,555 giây; log `output/huber-smoke-2026-10-12.log`. Run ứng dụng từ 14:32:07 đến 14:32:32
giờ Việt Nam. Chuỗi LOW store 7/item 34, seed 42, hai epoch/model, cả hai chọn epoch 2.

| Model (chỉ smoke) | MAE | RMSE | RMSE improvement vs Weekly | vs MSE |
| --- | ---: | ---: | ---: | ---: |
| Weekly | 5,376190 | 6,870861 | 0% | +3,268% |
| ResidualAR | 5,635561 | 7,102983 | -3,378% | 0% |
| ResidualARHuber | 5,635588 | 7,103008 | -3,379% | -0,000363% |

Đủ 10 artifact, 1 seed row, 4 epoch rows, 2 best flags và 5.040 predictions (2.520/model,
360/horizon/model), H1–H7 đầy đủ; khóa prediction không trùng, mọi prediction hữu hạn.
Dataset SHA-256 `038f25690a65149c94f86ddd3deceda20c037a5cfd754cafdfc539a72992f2ed` khớp
manifest. Scaler train min=2/max=38/scale=36. Metric tái tính từ CSV sai lệch tối đa
`2,642e-11`; `prediction−baseline−predicted_residual` tối đa `3,553e-15`.
So riêng ResidualAR với smoke lịch sử `2026-10-12_002237_861_gpu` (cùng dataset/protocol/seed),
mọi prediction khớp chính xác ở precision CSV; đây là regression check, không ghép metric vào report.
Huber thắng Weekly 0/1 và MSE 0/1 trong smoke; một seed không đánh giá được seed stability,
một LOW không đại diện MEDIUM/HIGH. Hai model gần bằng nhau không chứng minh Huber vô ích ở full.

Ở thời điểm smoke chưa có bảng chất lượng Huber 12×5; kết quả full mới ở đầu tài liệu
đã thay thế giới hạn này. Kết quả full MSE lịch sử nằm trong
[hồ sơ residual](residual-experiment.md), không phải đối chứng được ghép vào run mới.

Smoke chỉ kiểm tra pipeline. Full sau này vẫn chỉ trên 12/500 chuỗi, validation vừa chọn epoch
vừa báo metric, windows chồng lấn; chưa có test độc lập hoặc confidence interval. CSV hoàn chỉnh
xuất cuối run, chưa checkpoint/resume; kill JVM cưỡng bức có thể để manifest RUNNING.
Nếu Huber không đủ tốt, bước sau chỉ đề xuất residual + variance stabilization/log1p trong
một thí nghiệm riêng; chưa triển khai ở đây. Không commit/push.
