# Lịch sử triển khai

Các bước dưới đây được tổng hợp từ yêu cầu đã thực hiện, mã nguồn và kết quả chạy đã quan sát.
Số thứ tự phản ánh thứ tự triển khai, không khẳng định mỗi bước tương ứng một commit riêng.

## 01. Khởi tạo nền tảng Java/Maven

**Nhu cầu và lý do:** xây dựng đồ án bằng Java 11, có dependency và điểm chạy thống nhất trước khi phân tích dữ liệu.

**Thành phần:** [pom.xml](../pom.xml), [Main.java](../src/main/java/com/dl4j/salesforecast/Main.java).
Maven cấu hình Java 11, DL4J/ND4J 1.0.0-M2.1, DataVec, Apache Commons CSV,
logging, JUnit Jupiter và exec plugin. `Main` dùng đường dẫn tương đối từ thư mục gốc dự án.

**Kết quả:** có thể biên dịch và chạy qua `mvn compile exec:java`; dependency DL4J có sẵn
nhưng chưa có mô hình được huấn luyện. IntelliJ mở dự án dưới dạng Maven.

## 02. Thống kê toàn bộ dataset

**Nhu cầu và lý do:** hiểu quy mô và chất lượng dữ liệu trước khi chọn chuỗi để dự báo.

**Thành phần:** [SalesDataAnalyzer.java](../src/main/java/com/dl4j/salesforecast/analysis/SalesDataAnalyzer.java).
Đọc CSV, tổng hợp bản ghi, ngày, cửa hàng, sản phẩm, tổng/trung bình/min/max sales.
Bổ sung tổng sales theo cửa hàng, top 10 sản phẩm, số bản ghi/ngày sales bằng 0,
ngày thiếu và bản ghi trùng cho từng kết hợp store + item.

**Quyết định:** `sales` là số lượng bán, không phải doanh thu. Kiểm tra độ phủ dựa trên
khoảng ngày toàn dataset; kiểm tra khoảng trống bên trong dựa trên khoảng ngày riêng của từng cặp.
Tổng sales vẫn cộng bản ghi trùng, không tự làm sạch dữ liệu.

**Kết quả:** 913.000 bản ghi, 10 cửa hàng, 50 sản phẩm, 500 chuỗi;
2013-01-01 đến 2017-12-31, mỗi chuỗi đủ 1.826 ngày, không thiếu/trùng;
1 bản ghi sales bằng 0. Tổng sales 47.704.512; min 0, max 231.
Store 2 có tổng sales cao nhất (6.120.128); item 15 đứng đầu (1.607.442).

## 03. Cải thiện cách trình bày

**Nhu cầu và lý do:** dòng thống kê nhiều cột quá dài, khó xem trong console IntelliJ.

**Thay đổi:** nhóm theo cửa hàng; mỗi sản phẩm là một khối, mỗi chỉ số một dòng có căn lề;
tách phần tổng kết độ phủ.

**Kết quả:** giữ nguyên phép tính và số liệu; dễ xem trạng thái `OK`/`CHECK` và xác định chỉ số có vấn đề.
Đã biên dịch và chạy trên dataset thực tế trong quá trình triển khai.

## 04. Xuất báo cáo ra file

**Nhu cầu và lý do:** báo cáo 500 cặp dài, cần lưu để đọc lại và dùng cho đồ án.

**Thay đổi:** `SalesDataAnalyzer.analyze(String, Path)` tạo báo cáo trong bộ nhớ rồi ghi UTF-8;
tự tạo thư mục cha. `Main` ghi `output/sales-statistics.txt` và thông báo đường dẫn.
Overload chỉ nhận đường dẫn CSV vẫn hỗ trợ console.

**Kết quả:** mỗi lần chạy cập nhật báo cáo; không cần cuộn toàn bộ thống kê dataset trong console.
File bị ghi đè khi xuất thành công. Báo cáo chuỗi riêng và tiền xử lý bổ sung sau này vẫn in console.

## 05. Chuẩn bị quản lý mã nguồn và public GitHub

**Nhu cầu và lý do:** chia sẻ mã nguồn dễ chạy lại, tránh đưa build, dữ liệu và cấu hình máy cá nhân vào Git.

**Thay đổi:** `.gitignore` bỏ qua build, IDE, dataset, output, model/checkpoint, log và `.env`;
`.gitattributes` thống nhất xuống dòng. Thêm README, LICENSE MIT, CONTRIBUTING và CSV mẫu tự tạo.
Các file `.idea` đã được bỏ khỏi danh sách theo dõi, vẫn giữ trên máy.

**Kết quả:** có tài liệu sử dụng và điều khoản cho mã nguồn. Dataset bên thứ ba không được cấp lại
giấy phép bởi LICENSE của dự án. Các file có sẵn trên máy không có nghĩa đã commit hoặc push.

## 06. Phân tích riêng một chuỗi thời gian

**Nhu cầu và lý do:** chọn store 1 + item 1 để hiểu phân phối, chu kỳ thời gian và chuẩn bị giao diện cho preprocessing.

**Thành phần:** [TimeSeriesAnalyzer.java](../src/main/java/com/dl4j/salesforecast/analysis/TimeSeriesAnalyzer.java).
`analyze(filePath, storeId, itemId)` lọc CSV, sắp xếp ngày tăng dần;
tính mean, median, độ lệch chuẩn tổng thể (chia N), ngày zero/thiếu/trùng;
in trung bình theo thứ/tháng/năm và 10 bản ghi đầu/cuối.

**Quyết định:** `TimeSeriesResult` giữ ngày/sales tương ứng trong danh sách chỉ đọc, có getter;
không tự ghi đè bản ghi trùng. `isComplete()` cho bước sau quyết định có thể xử lý tiếp.
Ngày thiếu chỉ tính giữa ngày đầu/cuối của riêng chuỗi. Cặp không tồn tại hoặc dữ liệu sai báo lỗi.

**Kết quả store 1 + item 1:** tổng 36.468; min 4, max 50; mean 19,9715;
median 19; độ lệch chuẩn 6,7392; 1.826 ngày; không có sales bằng 0;
không thiếu/trùng/khoảng trống; trạng thái `OK`.
4 kiểm thử kiểm tra thống kê, thứ tự, lọc cặp, dữ liệu trùng/thiếu, năm nhuận và đầu vào lỗi đã đạt.

## 07. Tiền xử lý chuỗi để chuẩn bị học mô hình

**Nhu cầu và lý do:** chuyển chuỗi đã kiểm tra thành cặp input/target, tránh normalization dùng thông tin tương lai.

**Thành phần:** [SalesPreprocessor.java](../src/main/java/com/dl4j/salesforecast/preprocessing/SalesPreprocessor.java).
Nhận `TimeSeriesResult`, từ chối chuỗi không hoàn chỉnh; chia train/validation/test theo ngày;
fit Min–Max trên train; tạo sliding window với 30 ngày input và 7 ngày target.
Có overload cấu hình ngày chia và độ dài cửa sổ.

**Quyết định:** train 2013–2015, validation 2016, test 2017. Toàn bộ target phải thuộc cùng một tập;
input được lấy lịch sử quan sát trước đó, kể cả trước ranh giới tập.
Không xáo trộn hoặc cắt giá trị normalization về [0, 1]. Khi train không đổi, mẫu số dùng 1.
Giữ scaler để đảo phép biến đổi và giữ cả dữ liệu gốc/chuẩn hóa cùng ngày trong kết quả chỉ đọc.

**Kết quả:** train 1.095 ngày/1.059 cửa sổ; validation 366 ngày/360 cửa sổ;
test 365 ngày/359 cửa sổ. Min/max train là 4/43 (khác max 50 của toàn chuỗi).
4 kiểm thử preprocessing đạt; tổng hai lớp test là 8, không lỗi.
`mvn test exec:java` đã chạy thành công trong bước triển khai này.
Chưa chuyển sang tensor ND4J, lưu scaler ra file hoặc train LSTM.

## 08. Chẩn đoán kiểm tra commit của IntelliJ

**Nhu cầu và lý do:** IDE báo không hoàn tất commit/push.

**Kiểm tra:** ảnh hiển thị `Commit and push checks failed` với 21 cảnh báo:
method chưa dùng, sửa collection bất biến trong test, cảnh báo dependency gián tiếp.
Git có tên/email, không có lock hay hook chặn tại thời điểm kiểm tra.

**Kết quả:** xác định bước kiểm tra IDE là nguyên nhân được hiển thị;
giải thích test cố ý sửa collection để xác nhận exception và lưu ý chọn đủ file commit.
Không sửa dependency hoặc khẳng định các cảnh báo bảo mật đã được giải quyết.
Sau đó lịch sử Git quan sát được có commit `133f904` cho analysis/preprocessing.

## 09. Hệ thống hóa tài liệu và quy trình hoàn tất yêu cầu

**Nhu cầu và lý do:** lưu lại thứ tự, lý do, bằng chứng thay đổi để trình bày đồ án và tránh tài liệu tụt sau mã nguồn.

**Thay đổi:** thêm `docs`, `AGENTS.md` và skill `sales-project-progress`.
Mỗi yêu cầu hoàn tất có mục nhật ký và tên commit đề xuất; thay đổi chức năng cập nhật trạng thái/lịch sử tương ứng.

**Kết quả:** có điểm đọc tài liệu chung và quy trình áp dụng trong các phiên agent tiếp theo.
Chi tiết kiểm tra của yêu cầu này nằm trong [nhật ký](change-log.md).
Quy trình không tự commit/push và không phải Git hook.

## 10. Baseline cùng thứ tuần trước

**Nhu cầu và lý do:** cần một mốc đơn giản để xác định LSTM có cải thiện so với dự báo theo tính mùa vụ hàng tuần.

**Thành phần:** [WeeklyNaiveBaseline.java](../src/main/java/com/dl4j/salesforecast/evaluation/WeeklyNaiveBaseline.java).
Đánh giá mỗi target bằng quan sát sales thật trước đó 7 ngày (`t - 7`), trên raw sales;
tính MAE, RMSE theo từng horizon và toàn bộ các dự báo. `Main` chạy đánh giá trên test của preprocessing.

**Quyết định:** dùng cửa sổ test cuốn chiếu có sẵn và lịch sử quan sát của từng origin.
Do các cửa sổ trượt mỗi ngày, target chồng lấn; tính đủ 7 horizon ở mọi cửa sổ và nêu rõ
điểm tổng hợp gồm 2.513 dự báo (359 origin × 7), không xem chúng là 2.513 ngày riêng.

**Kết quả store 1 + item 1:** MAE tổng hợp 5,2774, RMSE 6,6393 sales.
MAE theo horizon 1–7 tăng từ 5,2312 lên 5,3175; RMSE tăng từ 6,6088 lên 6,6636.
359 dự báo cho mỗi horizon. LSTM chưa được huấn luyện để so sánh.

**Kiểm thử:** 2 kiểm thử baseline mới và 8 kiểm thử hiện hữu đạt; chạy `mvn test exec:java`
thành công, đọc đủ 913.000 dòng dataset và in kết quả theo horizon.

**Giới hạn:** mốc mới áp dụng cho store 1 + item 1; protocol là đánh giá cuốn chiếu,
không tương đương dự báo toàn bộ 7 ngày một lần từ một origin cố định.

Chi tiết hiện hành ở [trạng thái dự án](current-state.md); nhật ký yêu cầu ở [change-log.md](change-log.md).

## 11. Tensor ND4J và LSTM dự báo trực tiếp 7 ngày

**Nhu cầu và lý do:** chạy thử mô hình học sâu trên cửa sổ đã chuẩn bị, đồng thời kiểm chứng
shape tensor, fit và suy luận với phiên bản DL4J đang dùng.

**Thành phần:** [SalesLstmForecaster.java](../src/main/java/com/dl4j/salesforecast/model/SalesLstmForecaster.java).
Đổi batch thành features `[batch, 1, 30]`, labels `[batch, 7, 30]`, mask `[batch, 30]`.
LSTM 32 hidden units nối `RnnOutputLayer` identity/MSE 7 units; mask chỉ bật bước cuối,
nơi network phát vector dự báo 7 ngày.

**Quyết định:** Adam 0,001, seed 12345, tối đa 30 epoch mặc định. Chọn checkpoint có RMSE thấp nhất
trên validation, khôi phục tham số đó, sau đó mới tính test theo raw sales, từng horizon và tổng hợp.
Có overload cấu hình epochs/hidden units/learning rate cho smoke test và các thử nghiệm sau.
Không điều chỉnh cấu trúc để khớp test.

**Kết quả:** kiểm thử train 2 epoch với mạng 4-unit trên dữ liệu tự tạo xác nhận đầu ra
`[1, 7, 30]`, 7 dự báo hữu hạn, selection từ validation hoạt động.
Trên dữ liệu thật, DL4J train thành công 30 epoch, chọn epoch 30 (validation RMSE 8,1373).
Test: MAE 6,6032, RMSE 8,5910; baseline cùng thứ tuần trước lần lượt 5,2774 và 6,6393.
LSTM chưa đạt baseline; horizon 7 có RMSE 15,2724.

**Kiểm thử:** 1 kiểm thử ND4J/LSTM mới và toàn bộ suite; `mvn test` — 11 tests,
0 failures/errors, BUILD SUCCESS. `mvn compile exec:java` chạy được trên CSV thật,
in metric weekly naive lẫn LSTM.

**Giới hạn:** cấu hình mô hình chỉ là điểm khởi đầu; chưa có tuning, lưu model/scaler,
dự báo model đã nạp lại, hoặc huấn luyện nhiều chuỗi. Kết quả xấu hơn baseline cần được giữ nguyên báo cáo.

Chi tiết metric và kế hoạch tiếp theo trong [trạng thái hiện tại](current-state.md).

## 12. Tìm cấu hình LSTM bằng validation và gia cố kiểm tra

**Nhu cầu và lý do:** LSTM khởi đầu kém weekly-naive; cần xem lỗi theo horizon, chọn cấu hình mà
không dùng test trong vòng tìm kiếm, và xử lý một số dependency runtime có phiên bản bị ảnh hưởng
bởi lỗ hổng đã công bố.

**Thay đổi:** thêm grid ba cấu hình (16/32 units, hai learning rate), patience, lưu và khôi phục
checkpoint validation tốt nhất, chẩn đoán MAE/RMSE và phân phối dự báo theo horizon. Tách `fit`
khỏi `evaluateTest` để search chỉ nhận train/validation; chạy baseline validation trước search,
baseline test và model test sau khi chốt winner. Bổ sung kiểm tra shape, giá trị không hữu hạn,
tham số và ngày cửa sổ. CSV đọc UTF-8, từ chối store/item không hợp lệ và sales âm. Ghim Gson,
Commons Compress, Commons Net, Commons Lang lên các phiên bản đã xử lý CVE được xác định.

**Kết quả:** chạy dataset thật chọn 16 units, learning rate 0,001, epoch 30; validation MAE/RMSE
6,0911/7,7108. LSTM test MAE/RMSE 6,4400/8,1550, kém weekly naive 5,2774/6,6393. Cả ba cấu hình
đều chạm 30 epoch trong giới hạn thử, nên patience 5 chưa kích hoạt; validation còn tiến triển
đến epoch cuối. Không diễn giải test là tiêu chí chọn model.

**Kiểm tra:** `mvn test` — 11 tests, 0 failures/errors; `mvn compile exec:java` trên CSV cục bộ
— BUILD SUCCESS; dependency tree xác nhận các phiên bản đã ghim. Test split đã từng được quan sát
trong bước baseline trước đây, nên đây không còn là holdout chưa từng thấy.

**Giới hạn:** mô hình chỉ kiểm chứng trên một chuỗi và một seed; grid nhỏ, cấu hình tốt nhất vẫn
kém baseline. Việc ghim các CVE đã xác định không đồng nghĩa đã quét toàn bộ dependency graph.

**Commit đề xuất:** `feat: tune LSTM on validation and harden dependencies`

## 13. Bổ sung đặc trưng trễ, rolling và lịch cho LSTM

**Nhu cầu và lý do:** LSTM chỉ nhận sales thô theo 30 ngày nên chưa được cung cấp tín hiệu tường
minh về chu kỳ tuần, mức sales gần đây và thứ trong tuần.

**Thay đổi:** mỗi ngày input giờ có sáu kênh: sales Min–Max, sales lag 7 ngày, rolling mean 7 ngày,
rolling mean 14 ngày, day-of-week sin và cos. Rolling mean kết thúc tại ngày đang tạo feature; lag
và rolling chỉ dùng ngày hiện tại/quá khứ. Feature sales dùng scaler fit trên train; day-of-week
dùng mã hóa tuần tuần hoàn. Cửa sổ train bỏ 13 target đầu để ngày đầu input đã có đủ 14 ngày lịch
sử cần thiết, không điền giả trị. Tensor LSTM đổi từ `[batch, 1, 30]` sang `[batch, 6, 30]`;
labels 7 ngày và ranh giới split giữ nguyên. Thêm kiểm tra giá trị/shape và kiểm thử feature số học.

**Kết quả trên dataset thật:** train windows giảm 1.059 xuống 1.046; validation/test vẫn 360/359.
Grid chọn 32 units, learning rate 0,001, epoch 30; validation MAE/RMSE 5,5388/6,9524. Test
MAE/RMSE 5,8843/7,3887: tốt hơn LSTM cũ (6,4400/8,1550), nhưng vẫn kém weekly naive
5,2774/6,6393. Validation RMSE lớn nhất ở horizon 3 (7,8159), test lớn nhất ở horizon 7 (8,2492).
Không dùng test để chọn grid; test 2017 đã từng được xem trong các bước trước.

**Kiểm tra:** `mvn test` — 11 tests, 0 failures/errors, BUILD SUCCESS. Tích hợp ND4J xác nhận input
6 kênh và output 7 horizon. `mvn -B compile exec:java` trên `data/train.csv` — BUILD SUCCESS;
search và baseline in đủ metric validation/test.

**Giới hạn và tiếp theo:** mới một chuỗi và một seed; feature giúp giảm sai số nhưng chưa vượt baseline.
Tiếp theo nên so sánh decoder/forecast architecture khác bằng validation, sau đó mở rộng qua nhiều
cặp store-item và seed để xem cải thiện có lặp lại không.

**Commit đề xuất:** `feat: add causal time-series features to LSTM inputs`

## 14. So sánh direct và autoregressive LSTM trên validation

**Nhu cầu:** so sánh cách dự báo trực tiếp 7 output với cách dự báo từng ngày, feed prediction vào
lịch sử để lặp đến horizon 7; đối chiếu cả hai với weekly naive trước khi chạy nhiều seed.

**Thay đổi:** thêm `AutoregressiveLstmForecaster`. Model huấn luyện một bước kế tiếp bằng các cửa sổ
train, rồi rollout tuần tự 7 bước: mỗi prediction cập nhật sales history, lag-7, rolling mean 7/14
và weekday features cho bước tiếp. Mỗi epoch chấm forecast rollout trên toàn validation, lưu
checkpoint RMSE thấp nhất và áp dụng patience. Cấu hình AR dùng cùng seed, units và learning rate
với direct winner để so sánh kiến trúc; Main in kết quả weekly/direct/AR trên validation và không
tính metric test.

**Kết quả dataset thật:** weekly naive validation MAE/RMSE 5,3032/6,6216; direct 7-output LSTM
5,5388/6,9524; autoregressive LSTM 5,4384/6,7275. AR tốt hơn direct về RMSE 0,2249, nhưng weekly
naive vẫn tốt hơn cả hai. AR chọn epoch 15 và dừng sau 20 epoch; horizon 7 RMSE 6,7611.

**Kiểm tra:** `mvn test` — 11 tests, 0 failures/errors; bao gồm autoregressive fit và rollout 7 bước.
`mvn -B compile exec:java` với CSV thật — BUILD SUCCESS; so sánh có 2.520 dự báo trên mỗi phương
pháp validation. `Main` không tính metric test ở bước này.

**Giới hạn và tiếp theo:** so sánh chỉ một seed và một chuỗi, chỉ một config AR (units/rate được
chọn từ direct), weekly baseline còn thắng. Tiếp theo lặp direct/AR trên seed 42, 123, 2026, 7, 99,
báo mean ± std validation MAE/RMSE rồi mới cân nhắc mở rộng nhiều chuỗi.

**Commit đề xuất:** `feat: compare autoregressive and direct LSTM forecasts`

## 15. Đánh giá Direct và Autoregressive qua năm seed

**Nhu cầu:** xác định chênh lệch Direct–AR có phụ thuộc vào một lần khởi tạo trọng số hay không,
trên cùng tập validation và cấu hình.

**Thay đổi:** bổ sung seed tường minh cho cả hai forecaster và `ExperimentRunner` chạy lần lượt
seed 42, 123, 2026, 7, 99. Cả hai dùng 32 units, learning rate 0,001, tối đa 30 epoch, patience 5;
mỗi mô hình chọn checkpoint validation riêng. Runner in kết quả từng seed và mean ± sample std
(n−1), kèm weekly naive; không truy cập test. Thêm kiểm thử tính sample standard deviation và
kiểm tra seed đã dùng.

**Kết quả validation:** Direct MAE 4,8238 ± 0,2701, RMSE 6,0980 ± 0,3474; AR MAE
4,8502 ± 0,4442, RMSE 6,0287 ± 0,5050. Weekly naive MAE/RMSE 5,3032/6,6216. AR có mean RMSE
thấp hơn Direct 0,0693 nhưng thắng RMSE chỉ ở 2/5 seed; Direct thắng 3/5. Vì chênh lệch nhỏ,
độ phân tán AR cao hơn và mới có năm seed trên một chuỗi, chưa thể kết luận lợi thế AR ổn định.
Cả hai neural model có mean RMSE thấp hơn weekly naive trong lượt này. Không có metric test mới.

**Kiểm tra:** `mvn test` — 13 tests, 0 lỗi; `mvn -B compile exec:java` — đủ 10 lượt huấn luyện,
tổng thời gian 15:17 phút, `BUILD SUCCESS`. Runner chỉ dùng train/validation. `git diff --check`
và kiểm tra liên kết Markdown được chạy sau cập nhật tài liệu.

**Giới hạn và tiếp theo:** chỉ một cặp store-item, validation được dùng chọn epoch, cấu hình AR
mượn units/rate của Direct, không có kiểm định ý nghĩa. Tiếp theo mở rộng nhiều chuỗi và xác định
cách tổng hợp metric phù hợp; không chọn mô hình bằng test 2017 đã được quan sát trước đây.

**Commit đề xuất:** `feat: compare LSTM strategies across random seeds`

## 16. Đánh giá macro trên nhiều chuỗi đại diện

**Nhu cầu:** mở rộng đánh giá khỏi Store 1–Item 1 để xem baseline và hai LSTM có hành vi khác nhau
ở các mức doanh số khác nhau; không gộp mọi dự báo rồi để chuỗi scale cao chi phối metric.

**Thay đổi:** thêm `MultiSeriesExperimentRunner`. Nó tính mean sales/ngày trong train 2013–2015,
lọc profile có 1.095 ngày train duy nhất, chia các cặp thành tertile LOW/MEDIUM/HIGH và lấy đều
bốn đại diện mỗi nhóm (12 chuỗi). Với mỗi chuỗi, preprocessing và scaler được tạo riêng, scaler vẫn
fit train-only; chạy weekly naive cùng Direct/AR với cấu hình 32 units, learning rate 0,001, tối đa
30 epoch, patience 5 và seed 42, 123, 2026, 7, 99. Kết quả LSTM được bình quân qua seed trong mỗi
chuỗi trước macro-average không trọng số. Main ghi báo cáo vào `output/multi-series-validation.txt`.

**Kết quả validation:** macro Weekly naive MAE/RMSE 8,7365 ± 2,4252 / 11,1435 ± 3,1713;
Direct 10,3217 ± 4,1082 / 13,0076 ± 5,1836; Autoregressive 9,7157 ± 3,5308 / 12,1396 ± 4,4385.
Độ lệch chuẩn là sample std giữa 12 điểm macro theo chuỗi, không phải CI. AR thấp hơn Direct về macro
MAE 0,6060 và RMSE 0,8680, đồng thời có mean RMSE thấp hơn ở 11/12 chuỗi. Weekly naive vẫn thấp
hơn AR về MAE 0,9792 và RMSE 0,9961; vì vậy hai neural model chưa vượt baseline tổng thể.

**Kiểm tra:** `mvn test` — 15 tests, 0 failures/errors; chọn tertile và vị trí quantile có test.
`mvn -B compile exec:java` trên dataset thật — 12 chuỗi × 5 seed × 2 LSTM (120 fits), `BUILD SUCCESS`
sau 2:05 giờ; báo cáo kiểm tra có đủ 12 cặp và macro. `git diff --check` cùng link Markdown được
chạy sau cập nhật tài liệu.

**Giới hạn và tiếp theo:** chỉ 12/500 cặp; stratification từ giai đoạn train; mô hình dùng cùng cấu
hình đã chọn trước và validation chọn checkpoint; chưa có bootstrap/CI. Cần phân tích theo horizon và
từng tier để tìm nguyên nhân weekly naive tốt hơn trước khi mở rộng toàn bộ dữ liệu. Test 2017 không
được chạy và đã từng được xem trong các bước trước.

**Commit đề xuất:** `feat: evaluate forecasts across representative series`
