# Sales Demand Forecasting DL4J

Dự án Java hướng tới dự báo nhu cầu bán hàng với DeepLearning4J (DL4J).
Phiên bản hiện tại phân tích dữ liệu bán hàng CSV và xuất báo cáo thống kê dạng văn bản.
Đã có weekly naive, direct LSTM và autoregressive LSTM cho một chuỗi store-item; runner hiện lặp hai LSTM qua năm seed và chỉ đánh giá validation.

Xem [tài liệu quá trình thực hiện](docs/README.md) để theo dõi từng bước, lý do và kết quả.

## Chức năng

- Tổng số bản ghi, khoảng ngày, số cửa hàng và sản phẩm.
- Tổng, trung bình, nhỏ nhất và lớn nhất của `sales`.
- Tổng sales theo cửa hàng và top 10 sản phẩm trên toàn bộ cửa hàng.
- Số bản ghi sales bằng 0, số ngày có ít nhất một bản ghi bằng 0 và số ngày bằng 0 của từng cặp store + item.
- Kiểm tra ngày thiếu, khoảng trống bên trong chuỗi và bản ghi trùng ngày cho từng cặp store + item.
- Xuất báo cáo UTF-8 vào `output/sales-statistics.txt`, nhóm theo cửa hàng và sản phẩm.

`sales` là số lượng bán, không phải doanh thu tiền tệ. Báo cáo cộng tất cả bản ghi,
kể cả bản ghi trùng; phát hiện trùng không tự loại bỏ dữ liệu.

## Yêu cầu

- JDK 11.
- Apache Maven 3.6.3 trở lên, có lệnh `mvn` trong PATH.
- Kết nối mạng cho lần tải dependency đầu tiên. ND4J native có thể cần tải lượng dữ liệu lớn.

## Chạy dự án

Chạy các lệnh từ thư mục chứa `pom.xml`.

1. Đặt dữ liệu 2013–2017 tại `data/train.csv` để chạy toàn bộ pipeline mặc định.
   File mẫu tự tạo `examples/train.csv` chỉ đủ để thử các analyzer bằng lời gọi riêng;
   không đủ cho cấu hình chia tập 2013–2017 và cửa sổ 30 → 7 của `Main`.
2. Biên dịch và chạy:

   ```sh
   mvn compile exec:java
   ```

3. Mở `output/sales-statistics.txt`. Thư mục được tạo tự động; mỗi lần chạy sẽ ghi đè báo cáo.
   Console hiển thị đường dẫn báo cáo.

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
examples/train.csv                  # Dữ liệu mẫu tự tạo, được đưa lên Git
data/                               # Dữ liệu cục bộ, bỏ qua bởi Git
output/                             # Báo cáo được sinh ra, bỏ qua bởi Git
pom.xml                             # Dependency và cấu hình Maven
```

## Phân tích một chuỗi thời gian

`Main` cũng gọi `TimeSeriesAnalyzer.analyze("data/train.csv", 1, 1)` và in báo cáo
chuỗi đó ra console. Đổi hai ID để phân tích cửa hàng/sản phẩm khác.

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
`SalesPreprocessor`. `ExperimentRunner` giữ cấu hình 32 units, learning rate 0,001,
giới hạn 30 epoch và patience 5; chạy direct và autoregressive lần lượt với seed 42, 123,
2026, 7, 99. Mỗi seed chọn checkpoint theo validation của chính mô hình. Báo cáo gồm metric
từng seed, mean ± sample standard deviation (mẫu số n−1) và weekly naive cố định. Runner không
tính metric test; không tune lại cấu hình theo từng seed. Kết quả chạy mới nhất được ghi trong
[trạng thái hiện tại](docs/current-state.md).

## Tiền xử lý

`Main` gọi `SalesPreprocessor.preprocess(series)` và in tóm tắt cùng cửa sổ đầu
của mỗi tập ra console. `ExperimentRunner` chạy hai kiến trúc LSTM trên train/validation
và so sánh với weekly naive; Main hiện không đọc test hoặc in test metrics.
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
riêng cho autoregressive. `Main` gọi `ExperimentRunner`, in kết quả từng seed và tổng hợp weekly
naive vs direct LSTM vs autoregressive LSTM trên validation, không tính metric test.

Sau mỗi epoch lưu checkpoint tốt nhất theo metric validation phù hợp: RMSE direct 7 output hoặc
RMSE rollout AR. Cả hai dừng sớm sau 5 epoch liên tiếp không cải thiện.

Kết quả multi-seed hiện tại và mức ổn định so với weekly baseline nằm trong
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
