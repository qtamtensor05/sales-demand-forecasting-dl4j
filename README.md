# Sales Demand Forecasting DL4J

Dự án Java hướng tới dự báo nhu cầu bán hàng với DeepLearning4J (DL4J).
Phiên bản hiện tại phân tích dữ liệu bán hàng CSV và xuất báo cáo thống kê dạng văn bản.
Đã có Weekly Naive, Direct LSTM, Autoregressive LSTM và Residual Autoregressive LSTM; runner đánh giá
12 chuỗi store-item đại diện theo doanh số train, lặp các LSTM qua năm seed và chỉ dùng validation.

Xem [tài liệu quá trình thực hiện](docs/README.md) để theo dõi từng bước, lý do và kết quả.

## Chức năng

- Tổng số bản ghi, khoảng ngày, số cửa hàng và sản phẩm.
- Tổng, trung bình, nhỏ nhất và lớn nhất của `sales`.
- Tổng sales theo cửa hàng và top 10 sản phẩm trên toàn bộ cửa hàng.
- Số bản ghi sales bằng 0, số ngày có ít nhất một bản ghi bằng 0 và số ngày bằng 0 của từng cặp store + item.
- Kiểm tra ngày thiếu, khoảng trống bên trong chuỗi và bản ghi trùng ngày cho từng cặp store + item.
- Xuất báo cáo UTF-8 theo từng run vào `output/runs/<run-id>/`, nhóm theo cửa hàng và sản phẩm.

`sales` là số lượng bán, không phải doanh thu tiền tệ. Báo cáo cộng tất cả bản ghi,
kể cả bản ghi trùng; phát hiện trùng không tự loại bỏ dữ liệu.

## Yêu cầu

- JDK 11.
- Apache Maven 3.6.3 trở lên, có lệnh `mvn` trong PATH.
- NVIDIA GPU hỗ trợ CUDA, driver phù hợp và CUDA Toolkit 11.6; backend hiện tại là
  `nd4j-cuda-11.6-platform`.
- Kết nối mạng cho lần tải dependency đầu tiên. ND4J native có thể cần tải lượng dữ liệu lớn.

## Chạy dự án

### Pipeline ứng dụng một cửa hàng, nhiều sản phẩm

Pipeline nghiên cứu 12 chuỗi độc lập ở dưới vẫn được giữ nguyên. Chế độ mới dùng Store 1,
**toàn bộ 50 items học chung một Global ResidualAR-MSE mỗi seed**: full chỉ 5 global fits,
không phải 250 fits. Input 6 dynamic + 50 one-hot channels, per-item train-only scaler,
mini-batch 64, checkpoint theo validation macro RMSE qua items. Weekly tính lại đúng population.

```powershell
mvn -B compile exec:java '-Dexec.args=--global-store-smoke'
```

Smoke dùng items 1/25/50, seed 42, tối đa 2 epoch. Full, lưu đầy đủ log:

```powershell
$globalLog = 'output/global-full-' + (Get-Date -Format 'yyyy-MM-dd_HHmmss') + '.log'
mvn -B clean compile exec:java '-Dexec.args=--global-store=1' 2>&1 | Tee-Object -FilePath $globalLog
$LASTEXITCODE
```

Chạy từ root repository; không build/test khác khi đang train. Đổi store bằng `=2`;
có thể chỉ định `--batch-size=32` trong exec.args khi xử lý OOM, không giảm items/horizon.
Full5seeds chưa chạy. Benchmark50items/seed42/2epochs đã kiểm chứng tối ưu runtime:
20:03→17:41 trong ứng dụng,51/51tests PASS,126.000predictions khớp tại precisionCSV.
Giữ kết quả validation best epoch để xuất, vẫn chấm đủ từng epoch. Dùng script tạo build snapshot
riêng để tránh IDE ghi đè runtime classes; lệnh sau chạy benchmark, chỉ khi chủ động chạy full
mới đổi `Benchmark` thành `Full`:

```powershell
Set-Location 'C:\Users\quangTam\Documents\DL4j\sales-demand-forecasting-dl4j'
.\scripts\Invoke-GlobalRun.ps1 -Mode Benchmark
```

Script tự lưu log/Tee-Object và exitcode. `--global-store-benchmark[=1]` giữ tất cả50items,
chỉ giới hạn seed42/2epochs và ghi BENCHMARK ONLY; CLI full vẫn5seeds/30epochs/patience5/batch64.
Xem [hồ sơ global](docs/single-store-global-experiment.md)
cho protocol, memory/runtime, artifact schema 4, tests và smoke. Không so trực tiếp metric
Store1/50 items với kết quả 12-series cũ.

### Thí nghiệm ResidualAR MSE vs Huber

Chế độ `--residual-huber` chỉ train `ResidualAR` (MSE) và `ResidualARHuber` trong cùng run,
so với Weekly Naive: 12 chuỗi × 5 seed × 2 model = **120 fits**. Giữ nguyên pipeline residual,
chỉ thay loss. Huber chuẩn dùng delta cố định 1,0 trong scaled residual error space; checkpoint
vẫn chọn bằng validation RMSE sales. Không tuning delta hoặc dùng test 2017 để lựa chọn.

```powershell
mvn -B compile exec:java '-Dexec.args=--residual-huber-smoke'
```

Smoke chỉ 1 chuỗi/seed, tối đa 2 epoch/model. Full `2026-10-12_152631_034_gpu` đã SUCCESS
trên GPU sau 1:00:41: Huber RMSE 11,680508 gần bằng MSE 11,680474, chưa vượt Weekly 11,1435.
Lệnh đầy đủ lưu log và kết quả kiểm chứng nằm trong
[hồ sơ Huber](docs/residual-huber-experiment.md). Schema 3 ghi loss theo model và đổi tên cột
epoch loss thành `train_loss_scaled`/`val_loss_scaled`, thêm `loss_function,huber_delta`.
Các chế độ mặc định, `--residual`, `--residual-smoke` tiếp tục dùng MSE và schema cũ.

### Thí nghiệm Residual AR (chế độ riêng)

Lệnh không có tham số vẫn chạy Weekly Naive + Direct + AR (120 fits). Chế độ mới giữ nguyên
12 chuỗi đại diện, năm seed, split, sáu feature, MSE, Adam và cấu hình 32 units / 0,001 / patience 5.
Chỉ đổi target của model bổ sung thành residual quanh Weekly Naive:

```text
baseline(t) = observedSales(t - 7)
scaledResidual(t) = (actualSales(t) - baseline(t)) / trainSalesScale
prediction(t) = baseline(t) + networkOutput(t) * trainSalesScale
```

`trainSalesScale = trainMax - trainMin`, hoặc 1 nếu train không đổi. Không fit scaler residual
riêng; offset của sales scaler triệt tiêu khi lấy hiệu. Feature normalization không đổi, residual
có thể âm và không bị clip. AR feed **sales đã tái dựng** vào bước tiếp; H1–H7 đều lấy anchor
từ lịch sử đã quan sát tại origin. Checkpoint vẫn chọn theo RMSE rollout ở đơn vị sales gốc.

Smoke (chuỗi đầu trong cùng danh sách 12 đại diện, seed 42, ba model × tối đa 2 epoch):

```powershell
mvn -B compile exec:java '-Dexec.args=--residual-smoke'
```

Full comparison (12 × 5 × 3 = **180 fits**, tối đa 5.400 epoch):

```powershell
New-Item -ItemType Directory -Force output | Out-Null
$runLog = 'output/residual-full-' + (Get-Date -Format 'yyyy-MM-dd_HHmmss') + '.log'
mvn -B clean compile exec:java '-Dexec.args=--residual' 2>&1 | Tee-Object -FilePath $runLog
$runExitCode = $LASTEXITCODE
```

Chạy từ thư mục chứa `pom.xml`, không compile/test hoặc IDE auto-build vào `target/classes`
trong lúc training. Full residual thực tế gần nhất mất 3 giờ 16 phút cho 180 fits trên RTX 4050;
thời gian vẫn phụ thuộc early stopping và tải máy.

Mỗi lượt tạo `output/runs/<run-id>/`. Ngoài artifact trước đây, có `series-scaling.csv` lưu min/max,
scale train của từng chuỗi và `error-analysis.txt` so sánh bốn model theo macro, stratum, H1–H7,
seed, độ phân tán giữa seed và win rate từng chuỗi. `run.json` ghi `schemaVersion=2`, cấu hình,
dataset hash, Java/backend và `smoke`. `validation-predictions.csv` giữ nguyên các cột cũ và thêm
`predicted_residual` (sales gốc, chỉ có giá trị cho ResidualAR); `seed-metrics.csv` thêm bốn cột
residual cuối dòng; epoch history giữ schema và thêm model `ResidualAR`. Run cũ không bị sửa.
Full residual gần nhất là `2026-10-12_092618_825_gpu`: 180 fits trên GPU, `SUCCESS` sau
3 giờ 16 phút. ResidualAR đạt macro MAE/RMSE `9,2169/11,6805`, tốt hơn Direct và AR nhưng vẫn
kém Weekly Naive `8,7365/11,1435`; xem [phân tích đầy đủ](docs/residual-experiment.md).

Chỉ công nhận hoàn tất khi Maven exit code 0, `BUILD SUCCESS` và manifest `SUCCESS`.
Artifact smoke ghi rõ `SMOKE ONLY`, không chứng minh model vượt baseline. CSV hoàn chỉnh vẫn xuất cuối
run, không resume checkpoint. Lỗi Java được ghi `FAILED`; nếu hệ điều hành cưỡng bức dừng JVM hoặc
mất điện, manifest có thể còn `RUNNING`, tuyệt đối không coi đó là kết quả thành công.

### Chạy pipeline gốc

Chạy các lệnh từ thư mục chứa `pom.xml`.

Sau khi cài CUDA Toolkit, hãy mở terminal/IDE mới để nhận `CUDA_PATH` và thư mục
`C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA\v11.6\bin` trong `PATH`.

1. Đặt dữ liệu 2013–2017 tại `data/train.csv` để chạy toàn bộ pipeline mặc định.
   File mẫu tự tạo `examples/train.csv` chỉ đủ để thử các analyzer bằng lời gọi riêng;
   không đủ cho cấu hình chia tập 2013–2017 và cửa sổ 30 → 7 của `Main`.
2. Biên dịch và chạy:

   ```sh
   mvn compile exec:java
   ```

3. Mở `dataset-statistics.txt`, `summary.txt` và `error-analysis.txt` trong
   `output/runs/<run-id>/`; `output/runs/latest.txt` cho biết run gần nhất. Thư mục được tạo
   tự động cho từng lượt, không ghi đè run cũ. Mặc định phần thực nghiệm có 12 chuỗi × 5 seed × 2 LSTM
   (120 lượt huấn luyện), mất khoảng 2 giờ trên máy CPU đã đo; điều chỉnh `DEFAULT_SAMPLES_PER_STRATUM`
   trong `MultiSeriesExperimentRunner` nếu cần giảm số chuỗi khi thử nhanh.

Trong IntelliJ IDEA, mở dự án Maven, chọn JDK 11 và chạy
`com.dl4j.salesforecast.Main` với working directory là thư mục gốc dự án.
Đường dẫn đầu vào và đầu ra hiện được cấu hình trong `Main.java`.

## Định dạng dữ liệu

```csv
date,store,item,sales
2024-01-01,1,1,10
2024-01-02,1,1,0
2024-01-03,1,1,12
```

| Cột | Định dạng |
| --- | --- |
| `date` | Ngày dạng `yyyy-MM-dd` |
| `store` | Mã cửa hàng, số nguyên |
| `item` | Mã sản phẩm, số nguyên |
| `sales` | Số lượng bán, số nguyên |

Dữ liệu phải có header và giá trị hợp lệ; chương trình không tự sửa giá trị thiếu hoặc sai định dạng.
Thứ tự bản ghi không ảnh hưởng đến kiểm tra ngày liên tục.
`test.csv` và `sample_submission.csv` chưa được sử dụng.

## Cách hiểu kiểm tra chuỗi ngày

- **Unique days**: số ngày phân biệt của cặp store + item.
- **Zero-sales days**: số ngày có ít nhất một bản ghi sales bằng 0 của cặp đó.
- **Missing days**: số ngày thiếu so với khoảng từ ngày nhỏ nhất đến lớn nhất của toàn bộ dataset, tính cả hai đầu.
- **Internal gaps**: số ngày thiếu giữa ngày đầu tiên và cuối cùng của riêng cặp đó.
- **Duplicate records**: số bản ghi thừa cùng ngày trong một cặp.
- **OK**: đúng một bản ghi mỗi ngày trong toàn bộ khoảng thời gian; **CHECK**: thiếu ngày hoặc trùng bản ghi.

Chương trình kiểm tra mọi kết hợp giữa các cửa hàng và sản phẩm xuất hiện trong dataset.
Một kết hợp không có bản ghi sẽ được đánh dấu thiếu toàn bộ ngày. Quy tắc này giả định
mọi cửa hàng đều cần có dữ liệu cho mọi sản phẩm trong toàn bộ khoảng thời gian.

## Cấu trúc

```text
src/main/java/com/dl4j/salesforecast/
  Main.java                         # Điểm chạy, đường dẫn input/output
  analysis/SalesDataAnalyzer.java    # Tính và xuất thống kê
  analysis/TimeSeriesAnalyzer.java   # Phân tích riêng một chuỗi store + item
  preprocessing/SalesPreprocessor.java # Chia tập, chuẩn hóa và tạo cửa sổ
  evaluation/WeeklyNaiveBaseline.java # Baseline cùng thứ tuần trước, MAE/RMSE
  model/SalesLstmForecaster.java      # Direct LSTM dự báo 7 ngày
  model/AutoregressiveLstmForecaster.java # LSTM dự báo tuần tự từng ngày
  model/ExperimentRunner.java         # So sánh hai mô hình trên năm seed
  model/MultiSeriesExperimentRunner.java # Chọn tertile LOW/MEDIUM/HIGH và macro-average
examples/train.csv                  # Dữ liệu mẫu tự tạo, được đưa lên Git
data/                               # Dữ liệu cục bộ, bỏ qua bởi Git
output/                             # Báo cáo được sinh ra, bỏ qua bởi Git
pom.xml                             # Dependency và cấu hình Maven
```

## Phân tích một chuỗi thời gian

Để phân tích thủ công một chuỗi cụ thể, gọi `TimeSeriesAnalyzer.analyze` với store/item mong muốn.
`Main` hiện gọi analyzer ở chế độ yên lặng cho các cặp được chọn bởi thí nghiệm nhiều chuỗi.

```java
TimeSeriesAnalyzer.TimeSeriesResult result =
        TimeSeriesAnalyzer.analyze("data/train.csv", 1, 1);
// result.getDates() và result.getSales() có cùng thứ tự theo ngày tăng dần.
// Kiểm tra result.isComplete() trước khi dùng trong bước tiền xử lý.
```

Analyzer lọc CSV, sắp xếp theo ngày, tính min/max/mean/median và độ lệch chuẩn
tổng thể (chia cho N), rồi in trung bình theo thứ, tháng và năm cùng 10 bản ghi đầu/cuối.
Các danh sách kết quả chỉ đọc để giữ ngày và sales khớp nhau.

Ngày thiếu của chuỗi được tính trong khoảng ngày đầu–cuối của chính chuỗi.
`Duplicate dates` đếm số ngày có nhiều bản ghi; `Extra records` đếm số bản ghi dư.
Nếu có trùng, dữ liệu vẫn được giữ nguyên và thống kê tính trên mọi bản ghi;
trạng thái là `CHECK`. Không tìm thấy cặp hoặc dữ liệu không hợp lệ sẽ báo lỗi.

Pipeline dự kiến:

```text
train.csv -> SalesDataAnalyzer -> TimeSeriesAnalyzer
          -> SalesPreprocessor -> Normalization
          -> Sliding Window (30 ngày đầu vào, 7 ngày đầu ra)
          -> Weekly naive baseline + Direct/Autoregressive LSTM multi-seed validation
```

`TimeSeriesAnalyzer` cung cấp chuỗi đã sắp xếp và thông tin chất lượng cho
`SalesPreprocessor`. `MultiSeriesExperimentRunner` xếp hạng store-item theo mean sales 2013–2015,
chọn bốn chuỗi từ mỗi tertile LOW/MEDIUM/HIGH, rồi dùng cấu hình 32 units, learning rate 0,001,
tối đa 30 epoch, patience 5 và seed 42, 123, 2026, 7, 99. Mỗi scaler chỉ fit trên train của
chuỗi tương ứng. Với mỗi cặp, runner bình quân metric LSTM qua seed trước rồi macro-average không
trọng số giữa chuỗi. Báo cáo chính được ghi vào `output/multi-series-validation.txt`; phân tích
lỗi theo nhóm, horizon và seed được ghi vào `output/error-analysis-validation.txt`. Không tính test.
Xem kết quả và giới hạn trong [trạng thái hiện tại](docs/current-state.md).

Mỗi lượt chạy mới tạo thư mục riêng `output/runs/<yyyy-MM-dd_HHmmss_SSS>_<gpu|cpu>/`. Khi thành công,
thư mục chứa `run.json`, `console.log`, `dataset-statistics.txt`, `epoch-history.csv`,
`seed-metrics.csv`, `validation-predictions.csv`, `summary.txt` và `report.html`; `latest.txt` trỏ
tới run gần nhất. `run.json` ghi `RUNNING`, `SUCCESS` hoặc `FAILED`, backend và SHA-256 dataset.
Không chạy Maven compile/test khác hoặc bật IDE auto-build vào `target/classes` trong lúc training.

Trong lượt multi-series, mỗi epoch hoàn tất được in theo dạng
`[17/120] Direct | HIGH | store=2 item=43 | seed=42 | epoch=8/30`, kèm `trainLoss`, `valLoss` và
`valRMSE`. Mẫu số 120 là tổng số fit (12 chuỗi × 5 seed × 2 mô hình), không phải tổng số epoch;
early stopping có thể kết thúc một fit trước epoch 30. Khi lượt chạy hoàn tất, lịch sử từng epoch
được ghi vào `output/training-history-validation.csv` để vẽ biểu đồ.

Trong CSV, `train_loss_scaled_mse` và `val_loss_scaled_mse` là MSE cùng trên thang Min-Max fit
train-only nên có thể đặt chung một biểu đồ. `val_rmse_sales` là RMSE rollout/direct ở đơn vị sales
gốc và là metric dùng để chọn checkpoint. Việc chấm thêm hai loss cần hai forward pass mỗi epoch,
do đó lượt training có thể chậm hơn trước.

## Tiền xử lý

`Main` gọi `MultiSeriesExperimentRunner` để chạy đại diện LOW/MEDIUM/HIGH. Main lưu báo cáo
validation ở `output/multi-series-validation.txt` và phân tích lỗi ở
`output/error-analysis-validation.txt`; không đọc test hoặc in test metrics.
Mặc định:

| Tập | Thời gian | Số ngày | Cửa sổ 30 → 7 |
| --- | --- | --- | --- |
| Train | 2013–2015 | 1095 | 1046 |
| Validation | 2016 | 366 | 360 |
| Test | 2017 | 365 | 359 |

```java
SalesPreprocessor.PreprocessingResult prepared = SalesPreprocessor.preprocess(series);
SalesPreprocessor.Window window = prepared.getTrain().getWindows().get(0);
// window.getInput(): 30 sales đã chuẩn hóa, theo thứ tự thời gian.
// window.getInputFeatures(): [30 ngày][6 features] dùng cho tensor LSTM.
// window.getTarget(): 7 sales đã chuẩn hóa cần dự báo.
// window.getRawTarget(): sales gốc để đối chiếu và đánh giá.
double originalSales = prepared.getScaler().inverseTransform(0.5);
```

Có thể cấu hình bằng overload `preprocess(series, trainEnd, validationEnd, inputDays, forecastDays)`.
Hai ngày kết thúc được tính bao gồm; test là phần còn lại của chuỗi.
Mỗi tập phải có đủ dữ liệu để tạo ít nhất một cửa sổ; train còn cần 14 ngày feature history trước
cửa sổ input. Chuỗi thiếu ngày/trùng ngày bị từ chối.

Min–Max chỉ dùng min/max của train: `(sales - trainMin) / (trainMax - trainMin)`.
Validation/test có thể nằm ngoài [0, 1]; không cắt giá trị để giữ khả năng khôi phục sales.
Nếu train có giá trị không đổi, mẫu số dùng 1: train về 0, phép biến đổi vẫn đảo ngược được.
Scaler được giữ trong kết quả; chưa lưu ra file.

Cửa sổ dịch từng ngày, không xáo trộn. Toàn bộ 7 ngày target phải thuộc cùng một tập;
cửa sổ có target băng qua ranh giới tập bị bỏ. Input được phép dùng lịch sử trước ranh giới.
Validation/test mô phỏng dự báo tại từng thời điểm với sales thực tế đã quan sát trước đó,
không phải dự báo cả năm từ một thời điểm duy nhất.

LSTM nhận 6 features tại mỗi ngày input: sales chuẩn hóa, sales lag 7 ngày, rolling mean 7 ngày,
rolling mean 14 ngày, và day-of-week mã hóa bằng sin/cos theo chu kỳ tuần. Lag và rolling mean chỉ
dùng dữ liệu đến ngày input hiện tại; rolling mean bao gồm ngày hiện tại. 14 ngày đầu không có đủ
lịch sử cho feature nên không được dùng làm input window. Mọi giá trị sales-derived đều được scale
bằng min/max fit trên train; day-of-week không scale.

## Baseline cùng thứ tuần trước

`WeeklyNaiveBaseline.evaluate(prepared.getTest())` dự báo sales mỗi ngày bằng sales
của cùng thứ trong tuần trước (`t - 7 ngày`). So sánh với target gốc chưa normalization;
in MAE và RMSE theo từng horizon cùng điểm tổng hợp trên toàn bộ 7 horizon.
Mỗi cửa sổ test dùng quan sát thực đến cuối input làm lịch sử; cửa sổ trượt từng ngày.
Vì vậy các target ngày giống nhau có thể xuất hiện ở origin/horizon khác nhau.
Đây là đánh giá cuốn chiếu, không phải dự báo 7 ngày một lần cho cả năm.
Kết quả baseline là mốc tham chiếu cho LSTM; Main cũng tính baseline riêng trên validation
để hỗ trợ chọn cấu hình.

Với dataset hiện tại, store 1 + item 1 có 359 cửa sổ test, tức 2.513 dự báo qua 7 horizon;
MAE tổng hợp là 5,2774 và RMSE là 6,6393 đơn vị sales. Do cửa sổ chồng lấn,
đây là điểm dự báo trên các origin/horizon, không phải sai số trên ngày lịch duy nhất.

## LSTM dự báo 7 ngày

`SalesLstmForecaster.tuneOnValidation(...)` đã dùng grid ba cấu hình để chọn 32 units,
learning rate 0,001. `ExperimentRunner` cố định cấu hình đó và lặp Direct/Autoregressive trên seed
42, 123, 2026, 7, 99. Early stopping dùng patience 5, tối đa 30 epoch. Cấu hình không được tune lại
theo từng seed; như vậy độ biến thiên phản ánh tác động khởi tạo trong cùng thiết lập.
Runner báo mean ± sample standard deviation cho MAE/RMSE validation và so với weekly naive.
Nó không tính metric test; test 2017 đã được xem ở lần chạy trước.

Mạng được chọn dùng LSTM 32 hidden units theo sau bởi `RnnOutputLayer` có 7 đầu ra. Tensor input có shape
`[batch, 6, 30]`; labels `[batch, 7, 30]`. Label mask bật ở bước thời gian cuối để
network dự đoán đồng thời 7 ngày từ 30 ngày lịch sử. Inference lấy đầu ra ở bước 30
rồi đảo normalization bằng scaler đã fit trên train.

`AutoregressiveLstmForecaster` là kiến trúc so sánh: LSTM chỉ phát một giá trị sales ở bước kế tiếp.
Sau mỗi dự báo, chương trình nối prediction vào lịch sử, cập nhật lag/rolling features và dùng
weekday của ngày đích kế tiếp để dựng input mới, rồi dự báo tiếp cho đến đủ 7 ngày. Checkpoint của
mô hình này được chọn theo RMSE rollout 7 bước trên validation. Để so sánh kiến trúc có kiểm soát,
nó dùng cùng seed, hidden units và learning rate của direct model đã chọn; bước này chưa tìm grid
riêng cho autoregressive. `Main` gọi `MultiSeriesExperimentRunner`, in tiến độ seed và xuất macro
weekly naive vs Direct LSTM vs Autoregressive LSTM trên validation, không tính metric test.

Sau mỗi epoch lưu checkpoint tốt nhất theo metric validation phù hợp: RMSE direct 7 output hoặc
RMSE rollout AR. Cả hai dừng sớm sau 5 epoch liên tiếp không cải thiện.

Kết quả macro nhiều chuỗi và mức so sánh với weekly baseline nằm trong
[trạng thái dự án](docs/current-state.md). Test 2017 đã được quan sát trong các lần chạy trước,
nên không còn là holdout hoàn toàn chưa từng xem.

Chạy kiểm thử với `mvn test`.

## Dữ liệu và giấy phép

Dữ liệu thực tế trong `data/` không được phân phối cùng repository. Người sử dụng
tự chuẩn bị dữ liệu và tuân thủ điều khoản của nguồn cung cấp. Repository chưa xác nhận
nguồn hoặc giấy phép của các dataset cục bộ. `examples/train.csv` là dữ liệu tự tạo để minh họa.

Mã nguồn và dữ liệu mẫu tự tạo sử dụng [MIT License](LICENSE).
Giấy phép này không thay thế giấy phép riêng của dependency hoặc dữ liệu bên thứ ba.

## Đóng góp

Xem [CONTRIBUTING.md](CONTRIBUTING.md) để biết cách báo lỗi và gửi thay đổi.
