# Trạng thái hiện tại

## Pipeline ứng dụng: Store 1, global model cho 50 items

**GLOBAL IMPLEMENTED / TESTED / SMOKE TESTED / RUNTIME PROFILED / RUNTIME OPTIMIZED /
NUMERICAL EQUIVALENCE VERIFIED / FULL GLOBAL EXPERIMENT COMPLETED: YES.**
Run mới nhất `2026-10-13_025422_624_gpu` kết thúc SUCCESS/exit 0 lúc 03:48:25 ngày 2026-10-13,
Maven BUILD SUCCESS sau 54:08. Full dùng RTX 3090/JCublasBackend, batch 256/1024, cap 20,
patience 3 và ba seed. Seed 42/123/2026 chạy lần lượt 14/20/16 epoch; best epoch 11/20/13.
Validation Macro RMSE Global trung bình 8,655242 ± 0,127693, Weekly 10,380499, cải thiện 16,6202%;
Global thắng Weekly 50/50 items. Deployment chọn seed 123 epoch 20, Macro RMSE 8,542409.
Kết quả nằm trong `output/runs/2026-10-13_025422_624_gpu/`; model dùng được nằm tại
`deployment/model.zip`, log Maven tại `output/global-full-2026-10-13_025416_144-b2eebcf3.log`.
Đây là validation 2016 với target chồng lấn; test 2017 không được evaluate/selection và không phải
holdout pristine vì đã từng được quan sát lịch sử. Run cũ `2026-10-13_024351_527_gpu` còn manifest
RUNNING nhưng không có process hoạt động và đã bị run SUCCESS mới thay thế; không dùng làm kết quả.
Ba calibration artifact RTX 3090 hoàn thành ngày 2026-10-13 đã được đọc lại mà không training:
64/256 có RMSE 10,2836966038 và 245,840646 giây/epoch; 128/512 có RMSE 10,2957852301
và 135,626906 giây/epoch; 256/1024 có RMSE 10,3434761594 và 68,818477 giây/epoch.
Selector PowerShell đã sửa lỗi `Import-Csv` unwrap CSV một dòng thành scalar. Với baseline 64/256,
ngưỡng regression 1% cho limit 10,386533569838; cả ba candidate hợp lệ và policy hiện tại chọn
256/1024 vì nhanh nhất. Kết quả nằm tại
`output/global-calibration-selection-selector-fix-2026-10-13.json`. Full training chưa chạy.
Protocol Full hiện dùng batch 256/1024, seeds `[42,123,2026]`, hard cap 20 epoch/seed và
early-stopping patience 3. Source trước lượt sửa thực tế là 10/5, không phải 10/3 như giả định;
constants Full riêng đã được thêm để đạt đúng 20/3 mà giữ Smoke 2/5 và Calibration 1/5.
Validation Macro RMSE được tính sau mỗi epoch; improvement reset counter và lưu checkpoint,
ba epoch liên tiếp không cải thiện thì dừng, sau đó dùng best checkpoint. Test 2017 không tham gia.
CUDA trên máy đích Windows 10/RTX 3090 đã được khôi phục ngày 2026-10-13. Root cause là
classifier `windows-x86_64` của JavaCPP CUDA chỉ có JNI wrappers, trong khi classpath thiếu
`windows-x86_64-redist` chứa `cudart64_110.dll`, cuBLAS và cuDNN. `ServiceLoader` vẫn tìm thấy
`JCublasBackend`, nhưng `isAvailable()` reject backend do `jnicudart.dll: Can't find dependent
libraries`, rồi ND4J báo chung `NoAvailableBackendException`. `pom.xml` nay thêm đúng runtime
redist 11.6-8.3-1.5.7, không đổi DL4J/ND4J 1.0.0-M2.1.

Kiểm chứng mới: `BackendConfigurationTest` PASS; diagnostic synthetic xác nhận backend chính xác
`org.nd4j.linalg.jcublas.JCublasBackend`, một CUDA device, selected device 0, RTX 3090 cc 8.6,
tensor/matrix, LSTM forward, loss, backward/update, second forward, save/load và finite prediction
đều PASS. Full Maven suite **56/56 PASS**, 0 failure/error/skip. Global preflight dataset thật PASS:
Store 1, 50 items, 52.300 train windows, 18.000 validation origins, 126.000 forecasts/seed.
Không cài CUDA Toolkit, không đổi NVIDIA driver 616.56, không chạy calibration/full training;
không thay preprocessing, train-only scaling, split hoặc model protocol.
Ngày 2026-10-13 đã hoàn thiện source deployment-global dùng đúng ba seed `[42,123,2026]`;
pipeline nghiên cứu cũ vẫn giữ năm seed. Mỗi cải thiện validation Macro RMSE ghi atomically
`models/seed-<seed>/model.zip`, load-verify và so prediction trước/sau; kết thúc run ghi metadata
per-seed, scaler train-only, mapping 50 item, schema 56 kênh và copy checkpoint thắng vào
`deployment/`. Chọn model chỉ bằng validation Macro RMSE, tie-break Macro MAE rồi seed nhỏ hơn;
không dùng test 2017, không ensemble, không train lại. `--global-preflight` chỉ kiểm dữ liệu,
backend/config/counts và quyền ghi; `Invoke-GlobalTraining.ps1` cung cấp một lệnh portable,
`Audit-GlobalRun.ps1` kiểm SUCCESS cùng 150 metric rows/378.000 predictions. Full 3 seed chưa chạy,
vì vậy chưa có deployment model thực tế được chọn. Suite mới 53/53 PASS, không failure/error/skip;
chỉ dùng fixture synthetic và không chạy global experiment trên dataset thật.
Protocol GPU mới được xác nhận sau đó: workflow một lệnh chạy preflight → smoke → calibration
`64/256`, `128/512`, `256/1024` (train/validation batch) → full ba seed, tối đa 20 epoch.
Mỗi calibration dùng toàn bộ 50 items, seed 42 và đúng một epoch. Chính sách chọn là ứng viên
nhanh nhất có Macro RMSE không tệ hơn baseline 64/256 quá 1%; tie-break RMSE rồi train batch nhỏ.
Quyết định được ghi `calibration-selection.json` và copy vào run full. Đây là thay đổi có chủ ý
so với batch 64/max 30 trước đó; train-only scaler, split, feature, target và test exclusion giữ nguyên.
Sau thay đổi này full suite đạt 55/55 tests, 0 failures/errors/skips; selector PowerShell synthetic
chọn đúng 128/512 trong fixture khi 256/1024 nhanh hơn nhưng vượt ngưỡng RMSE. Không chạy dataset thật.
Ngày 2026-10-13: benchmark 50 items, seed 42, 2 epochs/batch 64 BEFORE
`2026-10-12_201423_002_gpu` và AFTER `2026-10-13_002648_831_gpu` đều SUCCESS/exit0.
Ứng dụng giảm 1.202,922 → 1.060,531 giây (1,1343×; giảm 11,84%). Giữ immutable validation
của best epoch để xuất cuối run, tránh chạy lại cùng checkpoint; vẫn đánh giá đủ 18.000 origins
× 7 mỗi epoch. Model.output calls 5.922→3.948; predictions xuất vẫn 126.000, scaler/window hashes,
Weekly, per-item/epoch/macro/pooled metrics giống nhau, max/mean prediction diff=0 tại precision CSV.
Suite mới 51/51 PASS, 0 failures/errors/skips; focused 12/12. Smoke qua script build cô lập
`2026-10-13_005123_869_gpu` SUCCESS, predictions CSV khớp smoke lịch sử cùng cấu hình.
Kiểm tra 47 liên kết local và git diff --check đạt. Chi tiết, giới hạn nhiễu timing,
profile và lệnh build cô lập tại [runtime optimization](single-store-global-experiment.md#runtime-optimization-ngày-2026-10-13).
Pipeline nghiên cứu 12 chuỗi bên dưới được giữ nguyên. CLI mới `--global-store=1` dùng toàn bộ
50 items, một Global ResidualAR-MSE mỗi seed: chỉ 3 global fits. Window 30→7, 6 dynamic +
50 one-hot channels, scaler train-only riêng item, train/validation batch do calibration chọn,
checkpoint validation macro RMSE.
Full dự kiến 52.300 train windows, 18.000 validation origins, 126.000 forecasts/seed;
đã train benchmark đủ 50 items/1 seed/2 epochs; chưa chạy full 3 seeds/tối đa 20 epochs.

Suite tại lần triển khai đầu46/46 tests, 0 failures/errors/skips, Maven BUILD SUCCESS. Smoke GPU
`2026-10-12_193415_770_gpu` SUCCESS (1:50 phút Maven), Store1 items 1/25/50, seed42,
2 epoch, best2, một model chung. Macro MAE/RMSE Global 8,686371/10,844962 và Weekly
8,606614/10,793978; pooled RMSE 11,247555 và 11,201497. Smoke thắng Weekly 0/3,
không là kết luận chất lượng 50 items/3 seeds.

Schema4 riêng có scaler/item-seed/prediction/epoch CSV, macro/pooled/item/horizon/tier/seed report.
Đủ 7.560 predictions, 3 scaler rows, 3 metric rows, 2 epoch rows và 1 best flag;
keys duy nhất, finite, hash đúng, metric recompute trong 4,9e-11, reconstruction dưới 1,5e-14.
Không dùng test 2017 selection; không tuning Huber/log1p/embedding hoặc thay pipeline cũ.
Full có thể dài: benchmark AFTER 50 items đo trung bình 8,772 phút/epoch; 150 epochs ước 21,93 giờ,
biến thiên theo hai epoch cho 15,1–28,75 giờ; early stopping chưa biết. Đây là ước lượng,
thay ngoại suy smoke 3 items trước đây. Xem [hồ sơ global](single-store-global-experiment.md)
cho thiết kế, tệp, tests, lệnh và giới hạn. Không commit/push.

## ResidualAR Huber — full hoàn tất ngày 2026-10-12

**IMPLEMENTED / TESTED / SMOKE TESTED / FULL EXPERIMENT COMPLETED.**
Run `2026-10-12_152631_034_gpu` SUCCESS trên JCublasBackend, 120 fits trong 1:00:41
(15:26:31–16:27:11 giờ Việt Nam), log Maven BUILD SUCCESS.
Macro MAE/RMSE MSE = 9,216867/11,680474; Huber = 9,215712/11,680508;
Weekly = 8,7365/11,1435. Huber không cải thiện macro RMSE, kém Weekly khoảng 4,82%,
thắng MSE 1/12 chuỗi và Weekly 0/12. MEDIUM/HIGH RMSE Huber 11,480410/15,454057,
nhỉnh cao hơn MSE; H5–H7 không cải thiện. Seed RMSE std đều khoảng 0,6358.
Artifact đủ 60 seed rows, 1.747 epoch rows, 120 best flags, 302.400 predictions;
hash đúng, keys duy nhất, metric tái tính khớp trong 5,1e-11, reconstruction trong 1,1e-10.
Không chạy training/test mới trong lượt báo cáo; 39 tests bên dưới thuộc lượt triển khai trước.

Chế độ `--residual-huber` so sánh Weekly, ResidualAR-MSE và ResidualARHuber trong cùng run;
12 chuỗi × 5 seed × 2 neural model = 120 fits. Không train lại Direct/AR. Chỉ thay loss;
giữ residual formulation, scaler train-only, features, split, architecture và checkpoint theo
validation RMSE sales. Custom ILossFunction tương thích ND4J 1.0.0-M2.1, không nâng dependency.
Delta cố định 1,0 trong scaled residual error space; không tuning. Nhãn train có |label| tối đa
0,6875 nên Huber có thể chủ yếu ở vùng quadratic (0,5 MSE), cần ghi nhận giới hạn này.

Suite mới `mvn -B test`: **39/39**, 0 failures/errors/skips, exit 0, BUILD SUCCESS.
Smoke `2026-10-12_143207_100_gpu` SUCCESS trên JCublasBackend/Java 11.0.32.1, LOW 7/34,
seed 42, hai epoch mỗi model; 4 epoch rows, 2 best flags, 1 seed row, 5.040 predictions.
Metric tái tính sai lệch tối đa `2,7e-11`, reconstruction dưới `4e-15`, không trùng khóa;
MSE predictions khớp hoàn toàn smoke MSE trước đó. Maven smoke mất 38,555 giây.

Smoke MAE/RMSE Weekly = 5,376190/6,870861; MSE = 5,635561/7,102983;
Huber = 5,635588/7,103008. Không dùng smoke kết luận Huber cải thiện hay vượt Weekly.
Kết quả full và các breakdown đã bổ sung trong hồ sơ Huber; smoke bên trên là bằng chứng lịch sử.
Schema 3 ghi loss theo model, delta và cột epoch loss trung tính; schema legacy/residual cũ giữ nguyên.
Chi tiết thiết kế, tests, artifact, lệnh full và ước lượng khoảng 2:11 giờ trong
[hồ sơ Huber](residual-huber-experiment.md). Không commit/push.

## Residual AR — full experiment ngày 2026-10-12

Đã triển khai Residual Autoregressive LSTM dưới chế độ riêng `--residual`; lệnh mặc định vẫn
chạy 120 fits của Direct/AR. Full residual chạy 12 chuỗi × 5 seed × 3 neural model = 180 fits,
cộng Weekly Naive không huấn luyện. Giữ nguyên split, danh sách đại diện, feature, MSE và cấu hình.

Target là `(sales(t)−observedSales(t−7))/trainSalesScale`; prediction bằng baseline cộng output
nhân scale. Không fit scaler residual mới; chỉ tái sử dụng scale sales train, offset triệt tiêu.
Rollout feed sales đã tái dựng và dùng anchor quan sát trước origin cho cả H1–H7. Chọn epoch bằng
RMSE finalPrediction ở sales gốc trên validation, không dùng test 2017 để chọn.

Báo cáo mới có đủ bốn model, macro hai tầng, stratum, horizon, seed và win rate so ba đối chứng.
Schema residual v2 thêm cột ở cuối CSV, lưu config thật và min/max/scale từng chuỗi; giữ nguyên run
cũ. Chi tiết thiết kế, tệp thay đổi, kiểm thử, lệnh và giới hạn tại
[thí nghiệm residual](residual-experiment.md).

**IMPLEMENTED / TESTED / SMOKE TESTED / FULL EXPERIMENT COMPLETED.** `mvn -B test` trước full run
đạt 33/33. Run `2026-10-12_092618_825_gpu` hoàn tất 180 fits trên CUDA, giữ nguyên dataset hash,
12 chuỗi, 5 seed và validation-only selection; không kết hợp Huber/log1p.

**SMOKE TESTED:** `mvn -B compile exec:java '-Dexec.args=--residual-smoke'` kết thúc exit 0,
BUILD SUCCESS trong 38,900 giây, run `2026-10-12_002237_861_gpu` có manifest SUCCESS, Java
11.0.32.1 và JCublasBackend. Chuỗi LOW store 7/item 34, seed 42, ba model × 2 epoch; đủ 6 dòng
history, 3 best flags, 1 dòng seed, 7.560 prediction (2.520/model) và 10 artifact. Tái tính metric
từ CSV khớp seed CSV trong dung sai `2e-9`; residual reconstruction khớp trong `2e-10`.
Đây là smoke pipeline, không phải kết quả so sánh mô hình đầy đủ.

### Kết quả full ResidualAR mới nhất

Run bắt đầu 09:26:18 và hoàn tất 12:42:22 giờ Việt Nam, thời lượng 3 giờ 16 phút 03 giây.
Manifest ghi `SUCCESS`, `JCublasBackend`, Java 11.0.32.1, 12 chuỗi, 5 seed, 3 neural model,
30 epoch tối đa và `testUsedForSelection=false`. Dataset SHA-256 trùng run chuẩn trước.

| Model | Macro MAE mean ± std | Macro RMSE mean ± std | RMSE so Weekly |
| --- | ---: | ---: | ---: |
| Weekly Naive | 8,7365 ± 2,4252 | 11,1435 ± 3,1713 | mốc so sánh |
| Direct LSTM | 10,3217 ± 4,1082 | 13,0076 ± 5,1836 | -16,73% |
| AR LSTM | 9,7157 ± 3,5308 | 12,1396 ± 4,4385 | -8,94% |
| ResidualAR | 9,2169 ± 2,6440 | 11,6805 ± 3,3958 | -4,82% |

ResidualAR giảm RMSE khoảng 10,20% so Direct và 3,78% so AR, đồng thời thắng Direct/AR ở 8/12
chuỗi. Tuy nhiên nó không thắng Weekly Naive ở chuỗi nào sau khi bình quân 5 seed; macro RMSE còn
cao hơn baseline 0,5370. Giả thuyết residual giúp neural model có tín hiệu, nhưng chưa đủ để thay
thế Weekly Naive.

Theo nhóm, ResidualAR RMSE là 8,1070 ở LOW, 11,4804 ở MEDIUM và 15,4540 ở HIGH. Nó kém Weekly
lần lượt 3,46%, 5,20% và 5,26%. So với AR tuyệt đối, residual kém ở cả 4 LOW nhưng tốt hơn ở cả
4 MEDIUM và cả 4 HIGH. Đây là câu trả lời chính cho mục tiêu nghiên cứu: residual thu hẹp đáng kể
vấn đề MEDIUM/HIGH, nhưng chưa đưa hai nhóm này vượt baseline mùa vụ.

ResidualAR tăng dần từ RMSE 11,5741 ở H1 lên 11,7889 ở H7; cả bảy horizon đều kém Weekly khoảng
3,85%–5,81%, nhưng tốt hơn AR tại mọi horizon. Độ nhạy seed thấp hơn hai model cũ: sample std của
macro RMSE là 0,6358, so với 0,9213 Direct và 0,9244 AR. Seed 42 gần baseline nhất (11,1603) và
thắng Weekly ở 5/12 chuỗi riêng lẻ; seed 123 tệ nhất (12,6071). Không seed nào có macro RMSE thấp
hơn Weekly.

Artifact có 60 dòng seed, 3.566 dòng epoch, 180 best flags và 453.600 prediction, chia đều
151.200/model và 64.800/horizon. ResidualAR chọn best epoch trung bình 9,55, chạy trung bình 14,55;
cả 60 fits đều dừng sớm. Tái tính MAE/RMSE từ prediction khớp seed metrics với sai lệch tối đa
`5,1e-11`; reconstruction `baseline + predictedResidual` khớp prediction trong khoảng `1,1e-10`.
Không chạy Maven test mới trong lượt cập nhật tài liệu này.

## Pipeline

```text
data/train.csv
  ├─ SalesDataAnalyzer → output/runs/<run-id>/dataset-statistics.txt
  └─ MultiSeriesExperimentRunner
       ├─ phân tầng tertile theo mean sales giai đoạn train
       ├─ TimeSeriesAnalyzer → SalesPreprocessor riêng cho mỗi chuỗi
       └─ Weekly Naive + Direct LSTM + Autoregressive LSTM (+ ResidualAR khi --residual)
            ├─ 5 seed; macro-average theo 12 chuỗi trên validation; không đọc test
            └─ Error analysis → output/runs/<run-id>/error-analysis.txt
```

`Main` hiện gọi `MultiSeriesExperimentRunner`. Runner xếp hạng các cặp bằng mean sales mỗi ngày
trên train (2013–2015), chia ba tertile LOW/MEDIUM/HIGH và chọn mặc định bốn quantile đại diện mỗi
nhóm. Mỗi chuỗi dùng scaler fit riêng trên train, cấu hình 32 units / learning rate 0,001, và cùng
năm seed 42, 123, 2026, 7, 99 cho Direct lẫn Autoregressive. Không tune lại cấu hình theo chuỗi/seed;
weekly naive tính một lần trên validation mỗi chuỗi. Macro trước hết lấy trung bình năm seed trong
từng chuỗi, sau đó lấy trung bình không trọng số giữa 12 chuỗi. Không đọc test.

`ExperimentRunner` giữ validation metric của Direct, Autoregressive và ResidualAR theo từng horizon
cho mỗi seed khi chạy chế độ residual. Báo cáo tổng hợp MAE/RMSE theo LOW/MEDIUM/HIGH, horizon 1–7
và seed; báo mean ± sample standard deviation, phần trăm cải thiện so với weekly naive và số chuỗi
thắng từng đối chứng theo RMSE. Neural metric được bình quân seed trong từng chuỗi trước khi
macro-average không trọng số giữa chuỗi. `Main` ghi báo cáo UTF-8 vào `error-analysis.txt` trong
thư mục run; bước tổng hợp không huấn luyện thêm và không tính metric test 2017.

Cấu hình LSTM dùng 30 ngày đầu vào, dự báo trực tiếp
7 ngày, Adam và seed tường minh. Mỗi ngày input có 6 kênh: sales, lag-7, rolling mean 7/14 ngày,
day-of-week sin/cos. Feature sales chỉ sử dụng ngày hiện tại/quá khứ; 14 ngày warm-up đầu không
được đưa vào cửa sổ. Tensor input `[batch, 6, 30]`, labels `[batch, 7, 30]`, mask `[batch, 30]`.
Grid thử (hidden units, learning rate): (16, 0,001), (32, 0,001),
(32, 0,0003), tối đa 30 epoch mỗi ứng viên, patience 5. Model checkpoint tốt nhất trong từng ứng
viên và ứng viên thắng đều được chọn theo RMSE validation. Fit/search không dùng dữ liệu test; lượt
chạy hiện tại không tính metric test. Autoregressive được fit với cấu hình thắng của direct model
(32 units, 0,001) để so sánh kiến trúc có kiểm soát; mỗi bước dự báo một sales value,
tạo lại lag/rolling/calendar features và
dùng giá trị dự báo làm lịch sử cho bước kế tiếp. Checkpoint AR được chọn bằng RMSE rollout 7 ngày.
`Main` chạy các so sánh trên validation; hiện không tính metric test.

## Kết quả multi-series validation gần nhất

Đã chạy 12 chuỗi (4 LOW, 4 MEDIUM, 4 HIGH) trên validation 2016. Tertile được xác định chỉ từ
mean sales theo ngày trong train 2013–2015; các chuỗi không có đủ 1.095 ngày train duy nhất bị loại
khỏi tập ứng viên. Báo cáo chi tiết từng cặp và macro được ghi ở
`output/multi-series-validation.txt` (file cục bộ được Git ignore); số liệu chính được lưu bên dưới.

| Nhóm | Store | Item | Mean sales train |
| --- | ---: | ---: | ---: |
| LOW | 7 | 34 | 16,7397 |
| LOW | 1 | 16 | 21,8311 |
| LOW | 7 | 21 | 26,3790 |
| LOW | 3 | 37 | 31,0292 |
| MEDIUM | 7 | 6 | 37,7361 |
| MEDIUM | 7 | 35 | 42,7763 |
| MEDIUM | 1 | 31 | 49,3443 |
| MEDIUM | 1 | 35 | 55,1534 |
| HIGH | 2 | 43 | 61,2292 |
| HIGH | 9 | 12 | 68,3991 |
| HIGH | 2 | 24 | 78,9160 |
| HIGH | 10 | 28 | 91,8986 |

Mỗi ô dưới đây là macro mean ± sample std giữa 12 chuỗi, tính trên metric đã bình quân năm seed
trong từng chuỗi. Đây là độ phân tán giữa chuỗi, không phải sai số chuẩn hay khoảng tin cậy.

| Phương pháp | Macro MAE | Macro RMSE |
| --- | ---: | ---: |
| Weekly naive | 8,7365 ± 2,4252 | 11,1435 ± 3,1713 |
| Direct LSTM | 10,3217 ± 4,1082 | 13,0076 ± 5,1836 |
| Autoregressive LSTM | 9,7157 ± 3,5308 | 12,1396 ± 4,4385 |

Weekly naive vẫn có macro MAE/RMSE thấp nhất. AR thấp hơn Direct 0,6060 MAE và 0,8680 RMSE theo
macro, nhưng vẫn kém weekly naive lần lượt 0,9792 MAE và 0,9961 RMSE. AR có RMSE bình quân seed
thấp hơn Direct ở 11/12 chuỗi; điều này không làm nó trở thành mô hình tốt nhất tổng thể vì baseline
tuần thắng cả hai neural model. Chưa đánh giá trên mọi 500 chuỗi, không có khoảng tin cậy/bootstrap,
và test 2017 không được đọc trong lượt này.

Từ 12 dòng per-series đã lưu, nhóm HIGH có sai số tuyệt đối lớn nhất: weekly/direct/AR RMSE lần
lượt 14,6811 / 18,7081 / 17,0007. Ở LOW, Direct và AR cải thiện RMSE so với baseline khoảng
4,25% và 5,54%; tại MEDIUM chúng kém 17,39% và 10,10%; tại HIGH kém 27,43% và 15,80%.
Direct thắng weekly naive theo RMSE ở 3/12 chuỗi, AR thắng ở 4/12. Artifact cũ chỉ giữ metric
per-series đã bình quân seed nên không thể xác định horizon hoặc seed nào gây lỗi lớn nhất; báo cáo
cục bộ hiện đánh dấu rõ hai phần này là chưa có dữ liệu thay vì suy diễn.

### Kết quả single-series multi-seed trước đó

Dataset cục bộ: 913.000 dòng, 500 cặp store-item, 1.826 ngày mỗi cặp, tổng sales 47.704.512.
Train 2013–2015 (1.046 windows sau warm-up), validation 2016 (360 windows), test 2017 (359 windows).
Train mất 13 target windows đầu vì cần đủ 14 ngày feature history trước cửa sổ input 30 ngày.
Scaler Min–Max fit trên train: min 4, max 43. Mọi chỉ số dự báo dưới đây ở đơn vị sales gốc.

ExperimentRunner cố định cấu hình đã chọn trước đó: 32 units, learning rate 0,001, tối đa 30 epoch,
patience 5; cùng seed được truyền cho Direct và AR. Mỗi mô hình chọn checkpoint theo RMSE validation
của chính mô hình. Mỗi seed có 2.520 dự báo cuốn chiếu chồng lấn. Độ lệch chuẩn bên dưới là độ lệch
chuẩn mẫu (n−1), metric sales ở đơn vị gốc.

Runner hiện in một dòng sau mỗi epoch đã hoàn tất, gồm vị trí fit toàn cục trên 120, kiến trúc,
nhóm sales, store-item, seed, epoch hiện tại/tối đa, train loss, validation loss và validation RMSE.
`ExperimentRunner.SeedResult` giữ lịch sử của cả Direct và AR; sau khi toàn bộ lượt multi-series
hoàn tất, `Main` ghi `output/training-history-validation.csv`. Hai cột loss là MSE trên cùng thang
Min-Max train-only; cột RMSE ở đơn vị sales gốc là metric validation dùng để chọn checkpoint.
Việc thu thập thêm loss không tác động scaler, split, feature nhân quả hay quy tắc chọn model chỉ
bằng validation, nhưng thêm hai lần chấm điểm/forward pass ở mỗi epoch nên có chi phí thời gian.

Lượt GPU ngày 2026-10-10 hoàn tất 120 fits nhưng lỗi classpath tại bước export cuối. Dữ liệu đã
được phục hồi từ log bất biến sang `output/training-history-validation.csv` với 2.693 epoch và
`output/seed-metrics-validation.csv` với 60 dòng seed. Kiểm tra phục hồi xác nhận đủ 120 fit, không
trùng khóa, mỗi fit đúng một best epoch và mỗi nhóm LOW/MEDIUM/HIGH có 20 dòng seed. Đây là dữ liệu
phục hồi từ stdout đã làm tròn (loss 8 chữ số, RMSE/MAE 4 chữ số), không phải export trực tiếp từ
đối tượng Java trong bộ nhớ. Parser tạm đã được xóa sau khi kiểm tra; log nguồn được giữ nguyên.
Báo cáo đọc nhanh từ các metric phục hồi nằm tại `output/multi-series-validation-recovered.txt`,
gồm bảng trung bình 12 chuỗi, macro theo LOW/MEDIUM/HIGH, macro toàn bộ và 60 dòng seed. Báo cáo
không có Weekly Naive hoặc horizon vì hai phần đó không xuất hiện trong log trước exception.

Các lượt chạy mới dùng `RunArtifactSession` và thư mục bất biến `output/runs/<run-id>/`. Manifest
`run.json` được tạo trước training với trạng thái `RUNNING`, dataset SHA-256, backend, seeds và quy
tắc validation-only; khi kết thúc chuyển nguyên tử sang `SUCCESS` hoặc `FAILED`. Thành công sẽ xuất
history epoch, metric seed, prediction validation từng origin/target/horizon kèm Weekly Naive,
summary TXT và report HTML. `console.log` tee stdout/stderr của ứng dụng từ sau khi session khởi tạo;
log Maven trước `Main` vẫn cần giữ bằng `Tee-Object` nếu cần. Prediction và bảng thành công hiện ghi
sau khi đủ 120 fits; nếu JVM lỗi giữa training, manifest/log còn nhưng CSV hoàn chỉnh chưa công bố.

| Seed | Direct MAE | Direct RMSE | AR MAE | AR RMSE |
| ---: | ---: | ---: | ---: | ---: |
| 42 | 4,9572 | 6,2297 | 5,1797 | 6,4511 |
| 123 | 4,5600 | 5,7554 | 4,8271 | 5,8999 |
| 2026 | 4,8098 | 6,1469 | 4,3362 | 5,4756 |
| 7 | 5,2072 | 6,5867 | 5,3982 | 6,6511 |
| 99 | 4,5847 | 5,7714 | 4,5100 | 5,6658 |
| Mean ± sample std | 4,8238 ± 0,2701 | 6,0980 ± 0,3474 | 4,8502 ± 0,4442 | 6,0287 ± 0,5050 |

Weekly naive giữ nguyên ở MAE/RMSE 5,3032/6,6216. Direct có MAE trung bình thấp hơn AR 0,0264;
AR có RMSE trung bình thấp hơn Direct 0,0693 (khoảng 1,14%) và thấp hơn weekly naive 0,5929.
Direct tốt hơn AR về RMSE ở 3/5 seed; AR tốt hơn ở 2/5. Cả hai LSTM có RMSE trung bình thấp hơn
weekly naive trong thí nghiệm này, nhưng AR có độ lệch chuẩn cao hơn Direct. Vì mức chênh RMSE
Direct–AR nhỏ và số seed chỉ là năm, chưa có bằng chứng rằng lợi thế của AR ổn định; đây cũng không
phải kiểm định ý nghĩa thống kê. Kết quả chỉ dùng validation, không tính metric test.

Lần đánh giá direct trước đó ghi test MAE/RMSE 5,8843/7,3887, baseline là 5,2774/6,6393.
Đây là kết quả một seed của phiên bản direct đã xem trước, không phải kết quả test của phép so sánh
kiến trúc hiện tại.

Các cửa sổ validation/test trượt từng ngày, dùng quan sát thực tế đến origin và chồng lấn target.
Do đó số dự báo đếm theo window × horizon, không phải số ngày lịch duy nhất.

## Rà soát an toàn và độ tin cậy

- Backend ND4J hiện là CUDA 11.6 (`nd4j-cuda-11.6-platform`). Smoke test đã nạp
  `JCublasBackend`, dùng CUBLAS và nhận RTX 4050 Laptop GPU compute capability 8.9 với khoảng
  6 GB VRAM. Terminal/IDE phải được mở lại sau khi cài Toolkit để nhận CUDA `bin` trong PATH.
- Đọc CSV dùng UTF-8; analyzer từ chối ID cửa hàng/sản phẩm không dương và sales âm.
- Baseline kiểm tra độ dài ngày/giá trị, thứ tự ngày mục tiêu và forecast horizon 1–7; forecaster
  kiểm tra tham số hữu hạn, shape tensor và dự báo hữu hạn.
- Runtime dependency tree ban đầu chứa Gson 2.8.0, Commons Compress 1.21, Commons Net 3.1 và
  Commons Lang 3.11. `pom.xml` hiện ghim lần lượt Gson 2.8.9, Compress 1.26.2, Net 3.9.0 và
  Lang 3.18.0; tree được xác nhận đang phân giải đúng các phiên bản này. Các bản cũ nằm trong dải
  bị ảnh hưởng bởi [CVE-2022-25647](https://nvd.nist.gov/vuln/detail/cve-2022-25647),
  [CVE-2024-26308](https://nvd.nist.gov/vuln/detail/cve-2024-26308),
  [CVE-2021-37533](https://nvd.nist.gov/vuln/detail/cve-2021-37533) và
  [CVE-2025-48924](https://nvd.nist.gov/vuln/detail/cve-2025-48924). Việc ghim đã xử lý các cảnh
  báo phiên bản đã xác định, nhưng không thay thế quét toàn diện liên tục.
- Số liệu quality ở trên phụ thuộc `data/train.csv` cục bộ và không thể tái lập nếu thiếu dataset.

## Kiểm chứng và giới hạn

Các kiểm thử bao gồm chia cửa sổ, kiểm tra analyzer, weekly baseline, hai forecaster, thống kê
sample standard deviation và chọn tertile đại diện. `mvn test` đạt 15 tests; `mvn -B compile
exec:java` chạy 12 chuỗi × 5 seed × 2 mô hình (120 lượt huấn luyện), `BUILD SUCCESS` sau 2:05 giờ;
không tính metric test.
Kết quả chi tiết trong [nhật ký](change-log.md).

Thí nghiệm mới chỉ lấy 12/500 chuỗi, dùng cùng cấu hình được chọn trước, và validation chọn epoch
ở từng seed nên vẫn là dữ liệu tuning. Macro std đo phân tán giữa các chuỗi, không phải độ bất định
của 500 chuỗi; chưa bootstrap hoặc đánh giá theo mọi cặp. Test 2017 đã được quan sát trong các bước
trước; lượt chạy này không đọc test.

Bước kế tiếp: phân tích vì sao ResidualAR seed 123 suy giảm và vì sao residual làm mất lợi thế LOW,
sau đó chỉ thử một thay đổi có kiểm soát, chẳng hạn Huber trên residual hoặc một cơ chế chọn/ensemble
seed định trước. Giữ nguyên split, feature, chuỗi, seed và train-only scaling; không dùng test 2017
để chọn giải pháp.

## Quy trình agent cho lượt huấn luyện dài

Skill dự án lưu toàn bộ stdout/stderr của các lệnh huấn luyện hoặc thí nghiệm dài vào log ổn định,
chờ tiến trình kết thúc rồi mới đọc và tổng hợp kết quả. Khi công cụ không thể chờ trong một lần gọi,
agent chỉ kiểm tra trạng thái sống/exit ở tần suất tối thiểu cần thiết, không `tail` hay đọc metric
từng epoch. Sau khi tiến trình dừng, agent đọc log một lần để lấy exit code, metric cuối, cấu hình
được chọn, thời lượng, cảnh báo và lỗi phục vụ kiểm chứng/tài liệu.

Log từng phần chỉ được đọc sớm khi tiến trình thất bại, có dấu hiệu treo, cần chẩn đoán, hoặc người
dùng yêu cầu theo dõi trực tiếp. Quy tắc này thay đổi cách agent quan sát tiến trình, không thay đổi
code huấn luyện, dữ liệu, train-only normalization hay ranh giới chia theo thời gian.

## Skill kỹ thuật của dự án

Repository có thêm ba workflow tự khám phá trong `.agents/skills/`. `debugging-and-error-recovery`
định tuyến chẩn đoán qua các biên CSV, thời gian, preprocessing, tensor, model và báo cáo; mặc định
chỉ chẩn đoán nếu người dùng chưa yêu cầu sửa. `test-driven-development` dùng JUnit 5 và fixture nhỏ
theo red-green-refactor cho thay đổi hành vi, bảo vệ train-only scaler, split thời gian, causal
features, shape/mask và validation-only selection. `code-review-and-quality` review theo findings,
ưu tiên data leakage, metric alignment, DL4J correctness, Java 11, test, security và performance;
mặc định không sửa code.

Ba skill dùng lại quy tắc log huấn luyện dài của `sales-project-progress`, không tự chạy thí nghiệm
120 fits, nâng dependency, commit hay push. Đây là thay đổi workflow agent; code Java, pipeline dữ
liệu, normalization và split hiện tại không đổi.

## Evaluation-only GlobalResidualAR trên Test 2017

Project có entry point riêng `GlobalTestEvaluationMain`, runner `GlobalTestEvaluationRunner` và script
`Invoke-GlobalTestEvaluation.ps1`. Đường chạy này chỉ restore ba checkpoint seed 42/123/2026 bằng
`GlobalModelArtifacts.loadCheckpoint`, gọi `network.output(input, false)` qua rollout causal hiện có,
và tuyệt đối không gọi `fit`, `setParams`, save checkpoint hoặc deployment finalization.

`SingleStoreGlobalDataset` hiện expose cả test samples. Với protocol đầy đủ 2013-2017, code kiểm chứng
359 origins/item, 17.950 origins cho 50 items và suy ra số prediction từ dữ liệu thay vì hard-code.
Scaler inference được đọc từ `global-series-scaling.csv`; scaler tính lại từ train chỉ dùng làm integrity
check. Runner kiểm tra SHA-256 dataset, metadata, epoch 11/20/13, deployment seed 123, coverage item,
Test targets chỉ thuộc 2017, key Weekly/model, duplicate và hash checkpoint trước/sau.

Output riêng gồm seed/item/horizon metrics, predictions, TXT, HTML và manifest runtime. Trạng thái hiện
tại là **IMPLEMENTED / TESTED / TEST 2017 NOT RUN**: chưa có test metrics trong lượt triển khai này và
không có thay đổi đối với Full runner, checkpoint, validation result hoặc deployment package.

## Run GPU Direct/AR trước Residual: 2026-10-10_184201_989_gpu

Manifest xác nhận trạng thái `SUCCESS`, backend `JCublasBackend`, Java 11.0.32.1, dataset SHA-256
`038f25690a65149c94f86ddd3deceda20c037a5cfd754cafdfc539a72992f2ed`, validation 2016 và
`testUsedForSelection=false`. Run bắt đầu 18:42:02 và hoàn tất 20:16:55 giờ Việt Nam, thời lượng
1 giờ 34 phút 52 giây. Artifact chuẩn nằm trong
`output/runs/2026-10-10_184201_989_gpu/`.

Kiểm tra tính đầy đủ ghi nhận 60 dòng seed, 2.693 dòng epoch, 120 fits, 120 cờ best epoch và
302.400 prediction validation. Mỗi model có 151.200 prediction; mỗi horizon 1-7 có 43.200.
Tính lại MAE/RMSE từ prediction cho từng bộ stratum/store/item/model/seed khớp seed metrics với
sai lệch lớn nhất khoảng `5,1e-11`; mọi fit có đúng 2.520 dự báo cuốn chiếu.

Macro validation Direct/AR của run này, sau khi bình quân năm seed trong từng chuỗi rồi bình quân
không trọng số qua 12 chuỗi:

| Phương pháp | MAE mean ± sample std | RMSE mean ± sample std | Cải thiện RMSE so Weekly Naive |
| --- | ---: | ---: | ---: |
| Weekly Naive | 8,7365 ± 2,4252 | 11,1435 ± 3,1713 | mốc so sánh |
| Direct LSTM | 10,3217 ± 4,1082 | 13,0076 ± 5,1836 | -16,73% |
| AR LSTM | 9,7157 ± 3,5308 | 12,1396 ± 4,4385 | -8,94% |

AR giảm RMSE 0,8680, tương đương khoảng 6,67%, so với Direct nhưng vẫn thua Weekly Naive 0,9961.
Direct thắng baseline tại 3/12 chuỗi và AR tại 4/12; toàn bộ các chuỗi thắng đều thuộc LOW. LOW là
nhóm duy nhất cải thiện baseline: Direct +4,25% RMSE và AR +5,54%. MEDIUM kém baseline 17,39% và
10,10%; HIGH kém 27,43% và 15,80% tương ứng. HIGH gây sai số tuyệt đối lớn nhất với RMSE Direct
18,7081 và AR 17,0007.

Theo horizon, Direct tệ nhất ở horizon 6 (RMSE 14,0661; -26,24% so baseline), trong khi AR tệ nhất
ở horizon 7 (12,3193; -10,58%). Direct gần baseline nhất ở horizon 4 (11,5853; -3,97%); AR gần nhất
ở horizon 1 (11,9795; -7,49%). Weekly Naive gần như ổn định quanh RMSE 11,14 trên cả bảy horizon,
cho thấy seasonality tuần là tín hiệu mạnh mà hai LSTM chưa khai thác ổn định.

Độ nhạy seed vẫn đáng kể. Macro RMSE Direct theo seed có sample std 0,9213, khoảng 12,0513-14,2827;
AR có sample std 0,9244, khoảng 11,1105-13,4595. Chỉ AR seed 2026 (RMSE 11,1105) nhỉnh hơn macro
Weekly Naive 11,1435; đây không phải bằng chứng lợi thế ổn định. Direct chọn best epoch trung bình
27,62 và chạy trung bình 29,70 epoch; 12/60 fit dừng sớm và 17/60 đạt best tại epoch 30. AR chọn
best trung bình 10,18, chạy trung bình 15,18 và cả 60/60 fit đều dừng sớm, phù hợp với dấu hiệu
validation rollout suy giảm sớm hơn dù train loss tiếp tục giảm.

Giả thuyết dự báo phần dư quanh Weekly Naive đã được kiểm chứng trong full run mới ở đầu tài liệu.
Kết quả cho thấy residual cải thiện Direct/AR tại MEDIUM/HIGH nhưng chưa vượt Weekly Naive.

Giới hạn: chỉ 12/500 chuỗi được chọn đại diện; validation vừa chọn epoch vừa đánh giá cấu hình cố
định; forecast windows chồng lấn; sample std phản ánh phân tán giữa chuỗi, không phải confidence
interval. Run không đánh giá test 2017 và chưa chứng minh khả năng tổng quát ngoài validation.

## Sửa launcher evaluation-only ngày 2026-10-13

POM dùng property `${exec.mainClass}` với giá trị mặc định vẫn là
`com.dl4j.salesforecast.Main`; riêng script evaluation truyền explicit
`com.dl4j.salesforecast.GlobalTestEvaluationMain`. Command training hiện có giữ hành vi cũ, còn
evaluation không route qua `Main`. Lời gọi `ExperimentConfiguration.fromArgs` thừa trong runner
evaluation cũng đã bỏ. Probe bằng argument `--invalid` dừng đúng tại usage của
`GlobalTestEvaluationMain` trước khi đọc dataset hoặc checkpoint. Trạng thái vẫn là
**IMPLEMENTED / TESTED / TEST 2017 NOT RUN**.

Lỗi report tier của lần chạy đầu đã được sửa: tên group chỉ được parse như horizon khi khớp chính xác
`H1` đến `H7`, vì vậy `HIGH` không còn bị parse chuỗi `IGH` thành số. Regression kiểm tra cả `HIGH`
và `H7`. Lần evaluation thất bại đã chạy inference nhưng không train; manifest xác nhận hashes trước/sau
khớp. Partial directory được giữ nguyên bằng cách archive thành
`test-evaluation-failed-2026-10-13_075006`; vị trí `test-evaluation/` sẵn sàng cho lần chạy lại.

## Nền tảng Big Data Docker (Giai đoạn 0-1)

Repository hiện có `compose.yaml` và `.env.example` cho MinIO, MinIO init, PostgreSQL, Spark master,
hai Spark worker, Spark job runner, Forecast Service, Backend API và Frontend. Dữ liệu MinIO/PostgreSQL
dùng named volume; toàn bộ service ở một bridge network. Cấu hình Compose đã parse thành công.

Forecast/API/Frontend hiện chỉ là bootstrap health endpoint để kiểm tra dependency và container lifecycle,
chưa chứa DL4J inference, Spring Boot REST hoặc React. Spark ETL, migration, API nghiệp vụ, dashboard,
benchmark và E2E chưa triển khai. Docker Desktop Linux engine đang tắt tại thời điểm kiểm tra, do đó
chưa có bằng chứng container thực chạy, Spark nhận hai worker hay checkpoint seed 123 restore trên CPU.

Audit hiện hành nằm tại `docs/00-repository-audit.md`. Run GPU `2026-10-13_025422_624_gpu` và deployment
package vẫn bất biến; test-evaluation manifest hiện có trạng thái `SUCCESS`, 17.950 Test origins,
376.950 forecast points cho ba seed, không training và hash checkpoint trước/sau giống nhau.

### Xác minh live sau khi bật Docker Desktop

Docker engine 29.8.2 và Compose 5.6.0 đã chạy. `docker compose up --build -d` thành công với 10 service.
Spark 3.5.9 Master ghi nhận hai worker `ALIVE`, mỗi worker 1 core và 1.024 MiB. MinIO init exit 0;
PostgreSQL, MinIO và ba HTTP bootstrap đều healthy. Frontend trả HTTP 200.

Bronze đã có `train.csv` 16 MiB cùng ingestion metadata; deployment package seed 123 đã có trong
`artifacts/models/global-residual-ar/seed-123`. Restart MinIO/PostgreSQL vẫn giữ object và probe row.
Module `spark-etl` có Store 1 quality gate, focused test 1/1 PASS. Silver/Gold và Spark runtime ETL vẫn
chưa chạy, nên Giai đoạn 3 chưa hoàn thành.

Checklist đối chiếu đầy đủ yêu cầu triển khai nằm tại `docs/11-implementation-checklist.md`. Tại lần
cập nhật sau triển khai ngày 2026-10-13, 10/12 tiêu chí nghiệm thu đã đạt và 2/12 chưa đạt. Full suite
sau thay đổi PASS 64/64, 0 failure/error/skip, `BUILD SUCCESS` trong 03:17. Đường găng hiện tại là
Spring Boot API/migrations, React dashboard qua API và benchmark Spark 1/2 Workers.

### Spark ETL và Forecast Service thực

Spark ETL đã đọc Bronze qua S3A trên hai worker, ghi Silver/Gold Snappy Parquet và fail-fast khi count
sai. Lượt chạy cuối xác nhận 91.300 raw/Gold rows, split 54.750/18.300/18.250 và window/origin
52.300/18.000/17.950. Runtime preprocessing parity PASS ở tolerance `1e-9`.

Forecast Service bootstrap đã được thay bằng DL4J CPU service. Container load checkpoint seed 123 đúng
SHA-256, trả health/metadata/H1-H7 và Weekly Naive. Forecast parity với artifact GPU PASS 21 điểm, max
diff `5,07e-5` ở tolerance `1e-3`. Checklist hiện là 10/12 đạt; còn frontend qua API và benchmark thật.

### Spring Boot API và React Dashboard thực

Backend bootstrap đã được thay bằng Spring Boot 2.7/Java 11. Flyway tạo schema nghiệp vụ, importer nạp
91.300 Store 1 rows vào PostgreSQL, API có OpenAPI, pagination, validation, global error handling,
forecast timeout và ingestion idempotency. Forecast end-to-end qua API trả 7 horizon và lưu đủ 7 rows.

Frontend bootstrap đã được thay bằng React 18/TypeScript/Vite với sáu màn hình; Nginx proxy `/api`
thành công và không để browser truy cập MinIO. Checklist tăng lên 11/12; chỉ tiêu chí benchmark 1/2
Workers còn chưa đạt. Browser automation không khả dụng nên chưa xác minh trực quan/responsive.

Dashboard hiện có hướng dẫn ba bước cho người mới, đổi H1-H7 thành “Ngày dự báo 1-7”, hiển thị ngày
đích và diễn giải prediction/Weekly Naive/Actual bằng tiếng Việt. Item được ghi rõ là mã sản phẩm ẩn
danh vì CSV không có tên hàng; không bổ sung tên giả. Frontend image mới đang healthy và bundle HTTP
đã được kiểm tra có đủ các nhãn hướng dẫn.

Dashboard Dự báo hiện đã thay phần hướng dẫn/ví dụ bằng kết quả nghiệp vụ trực tiếp. Mỗi ngày hiển thị
ngày lịch đầy đủ và ba dòng có đơn vị `sản phẩm`; phần đầu kết quả hiển thị tổng nhu cầu dự báo trong
bảy ngày. Thẻ kết quả dùng hai cột trên desktop và một cột trên màn hình hẹp. Frontend container đã
được build lại, healthy, HTTP 200 và bundle live chứa các nhãn mới; chưa có visual QA do trình duyệt
không khả dụng trong phiên.

Biểu đồ Phân tích hiện có trục ngày, trục số sản phẩm, vạch lưới, nhãn mốc và tooltip từng điểm; phía
trên có số thấp nhất, trung bình và cao nhất của chuỗi đang xem. Với Sản phẩm #1 năm 2017, API live
trả 365 ngày với min `5`, trung bình `22,2` và max `50` sản phẩm. Frontend mới đã được build/deploy,
container healthy và HTTP 200; chưa có visual QA do trình duyệt tích hợp không khả dụng.

Phân tích bán hàng hiện hỗ trợ tổng hợp Ngày/Tuần/Tháng, tổng nhu cầu, trung bình kỳ, kỳ cao nhất và
thay đổi so với kỳ trước. API `/api/analytics/monthly-demand` xếp hạng top 5 trong từng tháng trên cả
50 sản phẩm. Kiểm tra live năm 2017 trả 60 dòng cho 12 tháng; tháng 01/2017 đứng đầu là Sản phẩm #15
với 2.015 sản phẩm. Backend/frontend đều healthy và HTTP qua proxy hoạt động. Đây là thống kê lịch sử,
không phải forecast hoặc phân tích nhân quả; visual QA bằng trình duyệt vẫn chưa khả dụng.

Khối tổng dự báo bảy ngày hiện so sánh tổng prediction với tổng Weekly Naive của cùng kỳ tuần trước,
hiển thị chênh lệch sản phẩm và tỷ lệ tăng/giảm; trường hợp mẫu số bằng 0 được xử lý riêng. Kiểm tra
live Item #1, origin 2017-06-30 trả tổng dự báo `209,051`, cùng kỳ `215`, chênh `-5,949`, tương đương
nhu cầu giảm `2,8%`. Frontend container đã được build lại và healthy. `.gitignore` cũng đã bổ sung
artifact Node/frontend, output archive, PID/tmp và thư mục trạng thái container/Data Lake cục bộ.

### Ingestion CSV tự động

Hệ thống hiện có luồng hoàn chỉnh `Dashboard -> Spring Boot validation/checksum -> MinIO Bronze ->
spark-submit -> Silver/Gold/statistics -> PostgreSQL -> Dashboard`. Trạng thái lưu bền gồm `ACCEPTED`,
`RUNNING`, `COMPLETED`, `FAILED`; file cùng SHA-256 không được xử lý lại và run thất bại có endpoint
retry. PostgreSQL chỉ được cập nhật trong transaction sau khi Spark trả exit `0`; ingestion không gọi
training hoặc thay đổi model đang phục vụ.

Run smoke `4` đã hoàn thành với 4/4 raw/gold rows. MinIO có đủ object Bronze, Silver/Gold Parquet và
statistics JSON; PostgreSQL hiện có 91.304 sales rows với ngày lớn nhất `2018-01-02`. Upload lặp trả
run `4`, `duplicate=true`, attempts vẫn `1`. Backend/frontend đang healthy và frontend HTTP 200.

Kiểm thử hiện tại: backend 11/11 PASS, Spark 3/3 PASS, frontend test/build PASS. Chưa có visual QA do
Chrome và IAB không khả dụng; chưa có distributed lease/recovery khi backend dừng giữa job, CSV parse
trong RAM với giới hạn 50 MB và benchmark Spark 1/2 Workers vẫn chưa hoàn thành.

### Tiến độ ingestion chi tiết

Flyway V3 và API hiện lưu timeline theo attempt gồm `UPLOAD`, `VALIDATE`, `STORE_BRONZE`, `SPARK_READ`,
`WRITE_SILVER_GOLD`, `PUBLISH_POSTGRESQL`, `REFRESH_DASHBOARD`. Marker stdout của Spark cập nhật row
counter trong lúc job chạy. Dashboard polling hai giây, hiển thị phần trăm, elapsed time, throughput,
processed/total rows và trạng thái từng bước. Run runtime `5` hoàn thành đủ 7/7 stage, raw/gold `3/3`,
duration `29.731 ms`; backend/frontend đã recreate và đều healthy. IAB vẫn không khả dụng nên chưa có
visual/keyboard QA; chưa mở lại timeline chi tiết từ một dòng lịch sử.

### Trạng thái sau khi dọn dữ liệu upload mẫu (2026-10-14)

Theo yêu cầu người dùng, PostgreSQL đã xóa ingestion run `4`, `5`, `6`, pipeline `2`, `3`, `4`, toàn
bộ 14 stage events liên quan và 28 sales rows mẫu trong khoảng 2018-01-01..2018-01-10. Kiểm tra sau
transaction: các tập mục tiêu đều còn 0 dòng; API pipeline không còn trả ba run này. `sales_records`
đã trở lại 91.300 dòng và ngày lớn nhất 2017-12-31. File CSV trong repository và object MinIO được giữ
nguyên vì phạm vi yêu cầu chỉ là database; không thay model, checkpoint hoặc dữ liệu train cục bộ.

### Rà soát luồng triển khai Frontend Docker (2026-10-13)

Đã rà soát chi tiết luồng triển khai container frontend: multi-stage Dockerfile (`node:20-alpine` build static bundle và `nginx:1.27-alpine` runtime), reverse proxy `/api/` tới `backend-api:8084`, memory limit 128 MB và healthcheck qua `127.0.0.1`. Container hiện healthy, phục vụ cổng 3000 và hoàn toàn tách biệt với luồng training model (chỉ gửi request inference tới backend). Mã nguồn và cấu hình container giữ nguyên.

### Dự báo đồng thời 50 sản phẩm (2026-10-14)

Trang Phân tích hiện cho chọn một origin date và dự báo một lượt toàn bộ 50 sản phẩm. Mỗi dòng hiển
thị tổng dự báo bảy ngày, cùng kỳ tuần trước, chênh lệch và khoảng ngày; bấm sẽ mở đủ bảy ngày chi tiết.
Runtime thật trả đủ 50 x 7 trong 4,18 giây; forecast-service, backend-api và frontend đều healthy. Không
retrain, không đổi model, checkpoint, normalization hoặc chronological split. Chưa có browser QA thật.

### Sửa khả năng tìm thấy lựa chọn 50 sản phẩm (2026-10-14)

Màn hình Dự báo hiện có lựa chọn phạm vi rõ ràng và mặc định `Tất cả 50 sản phẩm`; người dùng có thể
đổi sang `Một sản phẩm`. Kết quả hàng loạt được hiển thị ngay trên cùng màn hình này. Ngày vẫn được giữ
vì đó là mốc bắt đầu forecast, không phải lựa chọn sản phẩm. Frontend container đã build lại, healthy,
HTTP 200 và bundle live có đủ nhãn phạm vi, lựa chọn tất cả cùng nút chạy tương ứng.
