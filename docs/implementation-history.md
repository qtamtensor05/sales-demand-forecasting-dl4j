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

## 17. Giảm đọc log trong lúc huấn luyện dài

**Nhu cầu:** khi agent chạy huấn luyện hoặc thí nghiệm dài, không liên tục đọc log kết quả từng
epoch; chỉ đọc và tổng hợp kết quả cuối cùng sau khi tiến trình hoàn tất.

**Thay đổi:** bổ sung quy tắc `Long-running training` vào skill dự án. Agent phải lưu đầy đủ
stdout/stderr vào log ổn định, chờ tiến trình kết thúc và chỉ sau đó đọc log để lấy exit code,
metric cuối, cấu hình được chọn, thời lượng, cảnh báo và lỗi. Nếu công cụ buộc phải kiểm tra nhiều
lần, agent chỉ kiểm tra trạng thái tiến trình ở tần suất tối thiểu, không đọc phần log đang tăng.
Chỉ đọc log sớm khi tiến trình lỗi, có dấu hiệu treo, cần chẩn đoán, hoặc người dùng yêu cầu live.

**Kết quả:** hướng dẫn agent nay phân biệt việc kiểm tra tiến trình còn sống với việc đọc log epoch,
giảm thao tác đọc và tránh tổng hợp các kết quả trung gian như kết quả cuối. Không thay đổi mã Java,
pipeline huấn luyện, train-only normalization hay ranh giới split theo thời gian.

**Kiểm tra:** đã gọi validator của `skill-creator` nhưng công cụ không chạy được vì môi trường
Python thiếu mô-đun `yaml`; frontmatter bắt buộc và tên thư mục được kiểm tra thủ công. Liên kết
Markdown và `git diff --check` được kiểm tra sau khi cập nhật tài liệu.

**Giới hạn:** validator tự động chưa chạy hoàn chỉnh do thiếu PyYAML. Đây là quy tắc thực thi cho
agent, không tự sửa cách chương trình Java ghi log. Agent vẫn có thể cần kiểm tra trạng thái tiến
trình do giới hạn timeout của công cụ và có thể đọc log sớm cho chẩn đoán hoặc khi người dùng yêu cầu.

**Commit đề xuất:** `docs: defer training log review until completion`

## 18. Bổ sung workflow debugging, TDD và code review cho agent

**Nhu cầu:** triển khai các nội dung skill đã được khảo sát và đề xuất, nhưng điều chỉnh theo Java
11, Maven, DL4J cùng các bất biến dữ liệu riêng của dự án thay vì cài nguyên bộ bên thứ ba.

**Thay đổi:** thêm ba skill project-native. `debugging-and-error-recovery` yêu cầu tái hiện, định vị
biên sai, kiểm chứng từng giả thuyết và phân biệt lỗi code với fixture/môi trường/dao động ngẫu nhiên.
`test-driven-development` áp dụng red-green-refactor bằng JUnit 5, dùng fixture tổng hợp nhỏ và bảo
vệ train-only scaler, chronological split, causal feature, tensor/mask cùng validation-only model
selection. `code-review-and-quality` báo findings theo mức độ nghiêm trọng, ưu tiên leakage, căn
chỉnh metric/horizon, DL4J correctness, Java 11, test, security và performance. Cả ba tuân theo
quiet-log workflow và không tự commit/push, nâng dependency hoặc chạy thí nghiệm dài.

**Kết quả:** agent có workflow chuyên biệt, tự khám phá cho chẩn đoán, triển khai có test và review,
trong khi quyền sửa code vẫn phụ thuộc yêu cầu người dùng. Không thay đổi mã Java, dữ liệu,
normalization, ranh giới split hoặc kết quả mô hình.

**Kiểm tra:** đã gọi validator `skill-creator` cho cả ba skill nhưng công cụ không khởi động vì
môi trường Python thiếu mô-đun `yaml`; thay vào đó kiểm tra thủ công frontmatter/tên thư mục và
placeholder cho cả ba skill. Kiểm tra liên kết Markdown cùng `git diff --check`. Không chạy Maven
vì thay đổi chỉ gồm hướng dẫn agent và tài liệu.

**Giới hạn:** validator tự động chưa chạy hoàn chỉnh do thiếu PyYAML; chưa forward-test hành vi của
skill trong phiên Codex mới và chưa benchmark hiệu quả so với workflow trước. Skill đưa ra quy
trình, không tự bảo đảm agent hoặc code không có lỗi.

**Commit đề xuất:** `feat: add project-native engineering agent skills`

## 19. Phân tích lỗi validation theo nhóm, horizon và seed

**Nhu cầu:** phân tích nơi Direct/Autoregressive LSTM thua weekly naive mà không đổi kiến trúc,
không huấn luyện thêm trong lượt triển khai và không suy diễn breakdown từ metric macro.

**Thay đổi:** thêm `ErrorAnalysisRunner` để nhận kết quả validation trong bộ nhớ và tổng hợp theo
LOW/MEDIUM/HIGH, horizon 1–7 và seed. Báo cáo dùng macro hai tầng: bình quân seed trong từng chuỗi,
sau đó bình quân không trọng số giữa chuỗi; kèm sample standard deviation, cải thiện phần trăm và
số chuỗi thắng baseline theo RMSE. `ExperimentRunner.SeedResult` nay giữ toàn bộ Direct validation
metrics thay vì chỉ MAE/RMSE tổng hợp. `Main` ghi `output/error-analysis-validation.txt` sau lượt
multi-series. Thêm JUnit synthetic cho metric, macro, độ lệch chuẩn, phân nhóm và định dạng.

**Kết quả quan sát từ artifact cũ:** chỉ breakdown theo nhóm có thể tái sử dụng hợp lệ. HIGH có
RMSE lớn nhất (weekly 14,6811; Direct 18,7081; AR 17,0007); LOW là nhóm duy nhất hai LSTM cải thiện
macro RMSE so với baseline. Direct thắng baseline ở 3/12 chuỗi và AR ở 4/12. Artifact không lưu
chi tiết seed/horizon nên chưa xác định horizon gây lỗi lớn nhất; file phân tích cục bộ ghi rõ phần
thiếu. Lần chạy sau sẽ sinh đầy đủ vì metric chi tiết nay được giữ trong `SeedResult`.

**Kiểm tra:** red test đầu tiên lỗi biên dịch vì chưa có runner; sau triển khai focused test đạt
3/3. Toàn bộ `mvn -B test` đạt 18 tests, 0 failures/errors/skips, `BUILD SUCCESS`. Không chạy lại
120 fits và không truy cập test 2017.

**Giới hạn và tiếp theo:** report đầy đủ horizon/seed cần chạy lại multi-series, ước khoảng 2 giờ
05 phút theo lượt trước. Sau khi có breakdown, kiểm chứng giả thuyết dùng loss/target tương đối hoặc
biến đổi ổn định phương sai fit train-only để giảm bất lợi trên chuỗi doanh số cao.

**Commit đề xuất:** `feat: add validation error analysis reporting`

## 20. Chuyển ND4J từ CPU sang CUDA backend

**Nhu cầu:** sử dụng RTX 4050 để chạy các phép toán DL4J thay cho backend CPU trước khi benchmark
lại workload multi-series.

**Thay đổi:** thay `nd4j-native-platform` bằng `nd4j-cuda-11.6-platform` cùng version
`1.0.0-M2.1`; thêm `BackendConfigurationTest` yêu cầu `JCublasBackend`. Không đổi model,
preprocessing, train-only scaling, chronological split hoặc validation-only selection.

**Kiểm chứng:** sau khi CUDA Toolkit 11.6 được cài, terminal cũ chưa nhận PATH nhưng các file
`nvcc.exe`, `cudart64_110.dll` và `cublas64_11.dll` đã tồn tại. Với CUDA `bin` thêm tạm vào PATH,
focused test nạp `JCublasBackend`, CUBLAS, CUDA build 11.6.55 và nhận RTX 4050 compute capability
8.9/6 GB; 1 test đạt. Toàn bộ suite GPU đạt 19 tests, 0 lỗi, `BUILD SUCCESS` trong 50,912 giây.

**Giới hạn:** chưa benchmark CPU/GPU trên cùng training workload và chưa chạy lại 120 fits. CUDA
chỉ được nhận trong tiến trình kiểm tra sau khi thêm PATH tạm; người dùng cần mở terminal/IDE mới
hoặc thêm CUDA `bin` cho terminal hiện tại.

**Commit đề xuất:** `build: switch ND4J training backend to CUDA`

## 21. Hiển thị tiến trình theo từng epoch của 120 lượt fit

**Nhu cầu:** trong lượt multi-series chạy lâu, hiển thị chính xác lượt fit và epoch đã hoàn tất theo
dạng `[17/120] Direct | HIGH | store=2 item=43 | seed=42 | epoch=8/30`.

**Thay đổi:** thêm `TrainingProgress` để tính số fit toàn cục theo thứ tự chuỗi, seed và hai mô hình.
`MultiSeriesExperimentRunner` truyền ngữ cảnh chuỗi vào `ExperimentRunner`; Direct và AR nhận ngữ
cảnh tùy chọn và in một dòng ngay sau khi đánh giá validation của mỗi epoch. Các overload cũ vẫn
hoạt động và không in progress khi chạy độc lập.

**Kết quả:** phép tính đầu/cuối cho 12 chuỗi, 5 seed và 2 mô hình cho ra 1/120 và 120/120; mẫu định
dạng yêu cầu được kiểm thử chính xác. Toàn bộ 21 test chạy bằng CUDA đạt, không có lỗi. Không chạy
lại thí nghiệm 120 fits.

**Giới hạn:** validation RMSE được tính mỗi epoch nhưng chưa xuất thành chuỗi thời gian; training
loss chưa được lưu. Do đó chưa thể vẽ biểu đồ train-loss/validation-loss từ log hiện tại. Lượt JVM
đã khởi động trước thay đổi phải được chạy lại mới có dòng progress mới.

**Commit đề xuất:** `feat: report multi-series epoch progress`

## 22. Thu thập train loss và validation loss theo epoch

**Nhu cầu:** lưu đủ dữ liệu để vẽ learning curve và quan sát overfitting trong lượt multi-series.

**Thay đổi:** sau mỗi lần `fit`, cả Direct và AR chấm `network.score` trên train và validation để
thu MSE trong không gian Min-Max. Epoch history giữ `trainingLoss`, `validationLoss` và validation
RMSE ở đơn vị sales. History được chuyển vào kết quả từng seed rồi xuất CSV tại
`output/training-history-validation.csv`, gồm định danh nhóm, store-item, model, seed, epoch, hai
loss, RMSE và cờ checkpoint tốt nhất. Dòng progress trực tiếp cũng in ba metric này.

**Kết quả:** regression test xác nhận hai forecaster expose loss hữu hạn và CSV dùng schema ổn định,
dấu thập phân độc lập locale. Focused tests đạt 5/5; toàn bộ suite CUDA đạt 23/23. Không chạy lại
120 fits và chưa tạo CSV dữ liệu thật trong lượt triển khai này.

**Giới hạn:** file CSV chỉ được ghi sau khi multi-series hoàn tất, nên JVM đang chạy hoặc lượt bị
ngắt giữa chừng không tạo artifact đầy đủ. Hai lần chấm loss thêm mỗi epoch làm tăng thời gian chạy.
AR validation loss là objective one-step/teacher-forced trên cùng thang với train; `val_rmse_sales`
mới là rollout 7 ngày dùng cho early stopping và lựa chọn model.

**Commit đề xuất:** `feat: export per-epoch training history`

## 23. Chuẩn hóa artifact theo từng lượt chạy

**Nhu cầu:** tách file cũ/mới theo run, lưu provenance và giữ prediction validation đủ để tái tạo
error analysis sau khi training thành công mà không cần model trong RAM.

**Thay đổi:** thêm `RunArtifactSession` tạo `output/runs/<timestamp>_<backend>/`, manifest trạng thái
ghi nguyên tử, SHA-256 dataset, application console log và con trỏ `latest.txt`. Hai forecaster giữ
prediction từng origin, target, horizon, actual, model prediction và Weekly Naive. Thêm exporter cho
seed metrics và validation predictions; khi thành công tạo epoch history, seed metrics, predictions,
summary TXT và report HTML. `Main` không còn ghi đè các file báo cáo tên cố định ở root output.

**Kiểm chứng:** red test thất bại vì chưa có session; focused tests sau triển khai đạt 4/4. Toàn bộ
suite CUDA đạt 24/24, 0 lỗi, `BUILD SUCCESS` trong 39,835 giây. Synthetic forecaster xác nhận số
prediction bằng forecast count, horizon/date hợp lệ và Weekly Naive hữu hạn. Không chạy 120 fits.

**Giới hạn:** CSV/report hoàn chỉnh được xuất sau khi training thành công, chưa append từng epoch/seed
ra đĩa; lỗi giữa lượt chỉ giữ manifest và application log. HTML hiện là báo cáo đọc nhanh, còn CSV
là nguồn cho biểu đồ chuyên sâu. Không lưu model checkpoint và không dùng test 2017 để selection.

**Commit đề xuất:** `feat: organize validation artifacts by run`

## 24. Xác nhận run GPU hoàn chỉnh và phân tích validation chi tiết

**Nhu cầu:** kiểm tra run đầu tiên dùng cấu trúc artifact theo run-id, phân tích kết quả thực tế và
cập nhật trạng thái dự án mà không sử dụng test 2017.

**Kết quả vận hành:** run `2026-10-10_184201_989_gpu` kết thúc `SUCCESS` trên `JCublasBackend` sau
1 giờ 34 phút 52 giây. Manifest lưu dataset hash, Java/backend, năm seed, 12 chuỗi, 30 epoch tối đa,
validation-only selection và xác nhận test không tham gia selection. Bộ artifact gồm console log,
dataset statistics, 2.693 epoch history, 60 seed metrics, 302.400 validation predictions, summary
TXT, report HTML và manifest.

**Kết quả mô hình:** Weekly Naive đạt macro RMSE 11,1435; Direct 13,0076 (-16,73% so baseline) và
AR 12,1396 (-8,94%). AR tốt hơn Direct khoảng 6,67% RMSE nhưng chỉ thắng baseline tại 4/12 chuỗi;
Direct thắng 3/12. LOW cải thiện baseline, còn MEDIUM/HIGH suy giảm; HIGH lớn nhất với RMSE
18,7081/17,0007 cho Direct/AR. Direct tệ nhất horizon 6, AR tệ nhất horizon 7.

**Kiểm chứng artifact:** đủ 120 fits và 120 best flags; prediction chia đều 151.200/model,
43.200/horizon và 2.520/fit. Tính lại metric từ prediction khớp seed CSV với sai lệch tối đa
`5,1e-11`. AR dừng sớm 60/60 fits, best epoch trung bình 10,18; Direct dừng sớm 12/60, best epoch
trung bình 27,62. Không chạy lại training hoặc Maven test trong bước phân tích tài liệu.

**Giả thuyết tiếp theo:** dự báo residual quanh Weekly Naive, kết hợp target/loss ổn định phương sai
fit train-only, có khả năng giữ seasonality tuần và giảm gradient bị chi phối bởi chuỗi doanh số cao.
Mọi so sánh tiếp theo tiếp tục chọn bằng validation; test 2017 chỉ được đánh giá một lần sau khi cố
định quyết định.

**Commit đề xuất:** `docs: record completed GPU validation run`

## 25. Thí nghiệm Residual AR có đối chứng, chưa chạy full

**Nhu cầu và lý do:** kiểm chứng giả thuyết LSTM học phần điều chỉnh quanh seasonal Weekly Naive
có cải thiện MEDIUM/HIGH mà không trộn thay đổi target với Huber, log1p, feature hay kiến trúc mới.

**Thay đổi:** thêm facade `ResidualLstmForecaster`, dùng chung train/rollout của AR; label bằng
`(actual−observedSales(t−7))/trainSalesScale`, prediction bằng baseline cộng output nhân scale.
Không fit scaler residual riêng; giữ scaler train-only, MSE, Adam, 32 units, learning rate 0,001,
patience 5, cùng cửa sổ và checkpoint validation RMSE. Sales đã tái dựng được feed vào rollout.
Thêm config opt-in `--residual` (180 fits) và `--residual-smoke` (3 fits × tối đa 2 epoch);
lệnh cũ vẫn 120 fits. Export schema v2 giữ cột cũ và bổ sung residual; manifest ghi config thật,
scaling từng chuỗi, báo cáo bốn model theo overall/stratum/horizon/seed/win rate. Không sửa run cũ.

**Kiểm chứng:** red JUnit thất bại do chưa có chức năng; sau triển khai/review `mvn -B test` đạt
33/33, exit 0, BUILD SUCCESS trong 1:12 phút. Có synthetic tests cho arithmetic, scaling không
leakage, future mutation, mask/shape, finite rollout, checkpoint, metric, runner/export và manifest.
Smoke thực tế hoàn tất exit 0/BUILD SUCCESS trong 38,900 giây; run `2026-10-12_002237_861_gpu`
ghi SUCCESS với 1 LOW (7/34), seed 42, 6 epoch, 3 best flags và 7.560 prediction. Recompute metric
khớp CSV trong `2e-9`, reconstruction trong `2e-10`; Java 11.0.32.1/CUDA. Chi tiết và tệp thay đổi
trong [hồ sơ residual](residual-experiment.md).

**Trạng thái và giới hạn:** IMPLEMENTED / TESTED / SMOKE TESTED / FULL EXPERIMENT NOT YET RUN.
Chưa kết luận residual vượt Weekly macro RMSE 11,1435 hoặc cải thiện MEDIUM/HIGH. Full 180 fits
ước khoảng 2:22 giờ từ run GPU cũ, thực tế tùy early stopping. CSV đầy đủ vẫn xuất cuối run;
không checkpoint/resume, kill cưỡng bức có thể để RUNNING. Không commit/push.

**Commit đề xuất:** `feat: add residual LSTM forecasting experiment`

## 26. Hoàn tất full Residual AR trên GPU và đánh giá giả thuyết

**Nhu cầu:** chạy phép so sánh có kiểm soát Weekly/Direct/AR/ResidualAR trên đúng 12 chuỗi và 5 seed,
sau đó xác định residual có giải quyết MEDIUM/HIGH hay không.

**Kết quả vận hành:** run `2026-10-12_092618_825_gpu` hoàn tất `SUCCESS` trên JCublasBackend,
Java 11.0.32.1, từ 09:26:18 đến 12:42:22 giờ Việt Nam (3:16:03). Manifest xác nhận 180 fits,
dataset hash không đổi, validation 2016, test không dùng để selection. Artifact gồm 60 seed rows,
3.566 epoch rows, 180 best flags và 453.600 validation predictions.

**Kết quả mô hình:** ResidualAR đạt macro MAE/RMSE 9,2169/11,6805, tốt hơn Direct
10,3217/13,0076 và AR 9,7157/12,1396, nhưng vẫn kém Weekly 8,7365/11,1435 khoảng 4,82% RMSE.
Residual thắng Direct và AR ở 8/12 chuỗi, nhưng không thắng Weekly ở chuỗi nào sau mean năm seed.
Tại MEDIUM/HIGH, residual thắng AR ở toàn bộ 8 chuỗi và giảm khoảng cách với Weekly xuống
5,20%/5,26%; tại LOW nó kém Weekly 3,46% và kém AR ở cả 4 chuỗi.

**Horizon, seed và học:** ResidualAR tốt hơn AR ở H1–H7 nhưng vẫn kém Weekly ở mọi horizon;
RMSE tăng từ 11,5741 (H1) lên 11,7889 (H7). Sample std macro RMSE giữa seed là 0,6358, thấp hơn
Direct 0,9213 và AR 0,9244. Seed 42 gần baseline nhất; seed 123 tệ nhất. Residual chọn best epoch
trung bình 9,55, chạy 14,55 epoch và dừng sớm 60/60 fits.

**Kiểm chứng và giới hạn:** dataset hash khớp; prediction đủ và không trùng khóa; metric tính lại
khớp seed CSV trong `5,1e-11`, reconstruction trong khoảng `1,1e-10`. Đây vẫn là validation trên
12/500 chuỗi, cửa sổ chồng lấn, chưa có CI/kiểm định và chưa đánh giá test 2017. Không sửa code,
không chạy test/training mới trong bước cập nhật tài liệu.

**Commit đề xuất:** `docs: record full residual validation results`

## 27. Thí nghiệm ResidualAR Huber có đối chứng MSE cùng run

**Mục tiêu và lý do:** cô lập tác động đổi loss lên residual forecasting, đặc biệt MEDIUM/HIGH,
giữ tất cả protocol khác và không train lại Direct/AR không cần thiết.

**Thay đổi:** thêm HuberLoss và LossMode vào vòng fit AR dùng chung. Kiểm tra JAR/source ND4J
1.0.0-M2.1 không có Huber chuẩn nên triển khai ILossFunction, không đổi dependency/Java 11.
Delta cố định 1 trong scaled residual error space; Huber chuẩn có vùng quadratic 0,5 MSE.
Train-only kiểm tra 12.552 labels cho max |label| 0,6875: có thể phần lớn error ở vùng quadratic,
không tự tuning delta. Giữ scaler, residual reconstruction, rollout và checkpoint RMSE sales.
CLI mới `--residual-huber` chạy 120 fits, smoke chạy 2 fits × 2 epoch; schema 3 phân biệt loss/model,
report macro/stratum/horizon/seed/win và improvement so Weekly/MSE. Mode cũ giữ hành vi MSE.

**Kiểm chứng:** TDD RED 2 failures đúng thiếu chức năng, focused 16/16; suite 39/39,
0 failures/errors/skips, BUILD SUCCESS. Smoke GPU `2026-10-12_143207_100_gpu` SUCCESS,
LOW 7/34, seed 42, đủ 5.040 predictions, 4 epoch rows, 2 best flags; metric khớp `2,7e-11`,
reconstruction dưới `4e-15`. MSE smoke predictions khớp smoke cũ. Review chưa thấy lỗi chặn.

**Giới hạn:** IMPLEMENTED / TESTED / SMOKE TESTED / FULL EXPERIMENT NOT YET RUN.
Smoke Huber RMSE 7,103008 và MSE 7,102983, Weekly 6,870861; không kết luận từ một LOW/seed.
Full 120 fits ước khoảng 2:11 giờ từ run trước 3:16:03; chưa có kết luận MEDIUM/HIGH, horizon xa
hoặc seed stability. Chi tiết/tệp/lệnh tại [hồ sơ Huber](residual-huber-experiment.md).
Không test selection năm 2017, không log1p, không commit/push.

**Commit đề xuất:** `feat: add Huber loss experiment for residual LSTM`


## 28. Hoàn tất và phân tích full ResidualAR Huber

**Mục tiêu và lý do:** cập nhật báo cáo từ run người dùng vừa train xong, kiểm tra artifact và
đánh giá Huber so MSE/Weekly cùng run, không train lại hoặc dùng test 2017 để chọn model.

**Kết quả:** run `2026-10-12_152631_034_gpu` SUCCESS, JCublasBackend/Java 11.0.32.1,
120 fits, 12 chuỗi, năm seed, delta 1, từ 15:26:31 đến 16:27:11 (1:00:40,79).
Log Maven BUILD SUCCESS; không có bản ghi exit code shell riêng.
MSE MAE/RMSE 9,216867/11,680474; Huber 9,215712/11,680508; Weekly 8,7365/11,1435.
Huber không cải thiện macro RMSE, thắng MSE 1/12 và Weekly 0/12. MEDIUM/HIGH và horizon
xa không cải thiện; RMSE seed std đều 0,6358. Chênh lệch rất nhỏ, không có kiểm định ý nghĩa.

**Kiểm chứng:** 60 seed rows, 1.747 epoch rows, 120 best flags (một/fit), 302.400 predictions,
2.520/fit, đủ H1–H7, finite và không duplicate keys. Dataset hash khớp; MAE/RMSE tính lại
khớp seed CSV trong 5,1e-11, reconstruction trong 1,1e-10. MSE/Huber chạy 873/874 epoch,
cả hai dừng sớm 60/60; chỉ một cặp có best epoch khác. Kiểm tra Markdown links và
`git diff --check` đạt. Không chạy Maven tests mới; 39 tests là bằng chứng lượt trước.

**Tệp cập nhật:** README.md, docs/README.md, docs/current-state.md,
docs/residual-huber-experiment.md, docs/implementation-history.md, docs/change-log.md.
Chi tiết bảng macro/stratum/horizon/seed trong [hồ sơ Huber](residual-huber-experiment.md).
Không sửa code, dependency, dữ liệu hoặc artifact; không commit/push.

**Giới hạn và hướng tiếp:** FULL EXPERIMENT COMPLETED nhưng Huber delta 1 chưa có lợi thế.
Không lưu phân bố training error nên chưa chứng minh tỷ lệ loss ở vùng linear. Chỉ 12/500 chuỗi,
validation dùng chọn epoch, windows chồng lấn, chưa CI/test độc lập. Chỉ đề xuất thí nghiệm riêng
residual + variance stabilization/log1p; không triển khai trong yêu cầu báo cáo này.

**Commit đề xuất:** `docs: report full residual Huber validation results`

## 29. Pipeline ứng dụng single-store global residual forecasting

**Mục tiêu và lý do:** bổ sung pipeline ứng dụng một store/nhiều items, một global model mỗi
seed; giữ nghiên cứu 12 chuỗi độc lập và không tiếp tục tuning Huber/log1p.

**Triển khai:** đọc panel Store một pass, kiểm tra đủ 50 IDs, tái sử dụng per-item preprocessing/
train-only scaling. Global ResidualAR-MSE nhận 6 dynamic + 50 one-hot channels, output1,
LSTM32/Adam0,001, mini-batch64, deterministic train shuffle. Một fit học tất cả items;
full năm seeds chỉ 5 fits. Rollout causal H1–H7 dùng residual utilities, checkpoint macro RMSE
sales qua items, pooled báo riêng. CLI global/smoke có storeId và batchSize, schema4 riêng
reuse immutable run lifecycle; không thay schema/behavior legacy.

**Tệp:** thêm SingleStoreGlobalDataset, GlobalBatch, GlobalMetrics, GlobalResidualLstmForecaster,
GlobalExperimentRunner/Report, GlobalArtifactExporter và GlobalStoreTest. Sửa Main,
ExperimentConfiguration, TimeSeriesAnalyzer (panel reader), AR (visibility helper causal),
RunArtifactSession; cập nhật README/docs index/current-state/history/change-log và
[single-store-global-experiment.md](single-store-global-experiment.md).

**Kiểm chứng:** TDD RED 2 failures/0 errors vì thiếu global CLI/dataset; preprocessing 3/3,
focused 6/6; sau thêm full-panel fixture, suite 46/46, không failures/errors/skips, exit0,
BUILD SUCCESS 1:53 phút. Có tests 50 items/52.300 windows, scalers, constant item, identity/mask,
batch/shuffle, no future leakage, shared synthetic fit, macro khác pooled, restore checkpoint,
artifact uniqueness và SUCCESS/FAILED; legacy tests giữ nguyên.

**Smoke:** `2026-10-12_193415_770_gpu` SUCCESS trên JCublasBackend/Java11.0.32.1,
Store1 items 1/25/50, seed42, 1 fit, 2 epoch/best2, Maven exit0/BUILD SUCCESS 1:50 phút.
Đủ 9 artifact, 3 scaling rows, 3 item-seed rows, 2 epoch rows, 1 best flag, 7.560 predictions.
Keys duy nhất, finite, H1–H7 đủ, hash và per-item train scalers khớp CSV. Metric tái tính
lệch tối đa 4,8131e-11; reconstruction 1,4100e-14. Global/Weekly macro RMSE
10,844962/10,793978, thắng0/3; không dùng smoke kết luận chất lượng full.

**Review và giới hạn:** không thấy lỗi chặn trong phạm vi review/test/smoke; Markdown links
và git diff --check đạt. IMPLEMENTED / TESTED / SMOKE TESTED / FULL GLOBAL EXPERIMENT NOT RUN.
Full50 chưa train, chỉ kiểm tra loader/coverage; chưa có win rate50, seed stability hoặc kết luận
so Weekly toàn Store1. Ngoại suy smoke 9,5–11 phút/epoch, tối đa24–28 giờ cho150 epoch;
early stopping có thể giảm, chưa đo thực tế full. Không auto-run full, test selection2017,
embedding/ensemble/tuning. Chưa checkpoint disk/resume; không commit/push.

**Commit đề xuất:** `feat: add single-store global residual forecasting`

## 30. Profile và tối ưu runtime global, kiểm chứng trước/sau

**Mục tiêu và lý do:** giảm runtime mà giữ nguyên model, dữ liệu, train order, scaler,
validation population, checkpoint và số liệu; chuẩn bị full command, không chạy full5seeds.

**Triển khai:** thêm nanoTime profile, benchmark riêng50items/seed42/2epochs, hash exact ordered
windows và script audit. Profile xác định train/inference chiếm gần toàn bộ runtime; tensor,
features và export nhỏ. Giữ immutable validation snapshot của best epoch để xuất cuối fit,
tránh inference lại cùng weights; vẫn đánh giá đủ mọi epoch. Thêm script PowerShell tạo build
snapshot riêng, lưu log/exitcode để tránh IDE ghi đè classes khi train.

**Kiểm chứng ngày2026-10-13:** TDD RED đúng thiếu benchmarkCLI và thừa final evaluation;
focused12/12, fullsuite51/51 PASS, 0 failures/errors/skips, exit0/BUILD SUCCESS2:12.
BEFORE `2026-10-12_201423_002_gpu`, AFTER `2026-10-13_002648_831_gpu` đều SUCCESS/exit0.
Ứng dụng1.202,922→1.060,531s (1,134×, giảm11,84%). Outputcalls5.922→3.948 do tái sử dụng
checkpoint result; mỗi epoch vẫn18.000origins×7. Audit đủ126.000rows, scalerCSV/Weekly/keys/
train-validation window hashes giống nhau; predictions, per-item/epoch/macro/pooled metrics
diff0 ở precisionCSV, bestepoch2 ở cảhai; tolerance cố định1e-9. Không đổi dependencies.

**Giới hạn:** một cặp benchmark khác giờ/tải máy; train chậm hơn130s dù code không đổi,
không diễn giải validationtotal2,01× là inferencekernel2×. AFTERmean8,772phút/epoch,
150epochước21,93giờ, biến thiên tốc độ hai epoch cho15,1–28,75giờ; earlystop chưa biết.
Cache thêm một snapshotCPU, không cacheGPU/alltraintensors. RunbaselineđầuFAILED do class
mismatch giữ làm evidence, không dùng vào phép so sánh. Chưa full5seeds/testselection2017.
Automation10phút đã dừng theo yêu cầu. Chi tiết tại [hồ sơ global](single-store-global-experiment.md).

**Tệp:** profile/fingerprint/tests/oracle mới; configuration/globalrunner/forecaster/report,
panel reader/preprocessor overload, artifactexport/session/Main; scripts audit và Invoke-GlobalRun;
README/docs index/current-state/history/change-log/global. Không commit/push.

**Commit đề xuất:** `perf: optimize global forecasting runtime without changing protocol`

**Bàn giao:** smoke script `2026-10-13_005123_869_gpu` SUCCESS/exit 0/BUILD SUCCESS,
predictions CSV cùng SHA256 với smoke lịch sử cùng cấu hình. 47 liên kết local và
`git diff --check` đạt; lịch kiểm tra mỗi 10 phút đã PAUSED.

## 31. Hoàn thiện checkpoint và deployment package cho global 3 seed

**Yêu cầu:** chuẩn bị source portable để máy GPU mới chạy Store 1, 50 items, ba seed
42/123/2026 bằng một command; không chạy full training trong lượt chuẩn bị.

**Lý do:** pipeline trước giữ best params trong RAM và global dùng 5 seed, chưa có model load lại,
scaler/schema/mapping phục vụ inference hoặc selector deployment có thể audit.

**Thay đổi:** tách seed global mới khỏi năm seed nghiên cứu cũ; thêm checkpoint DL4J atomic,
load/prediction verification, metadata per-seed, selector validation-only có tie-break, package
deployment tự chứa, preflight và script training/audit. Giữ Java 11, model, 32 units, MSE,
Adam 0,001, batch 64, patience 5, tối đa 30 epoch, train-only scaling và split thời gian.

**Kiểm chứng:** chỉ JUnit synthetic, không chạy global training/benchmark/smoke dataset thật.
Test bảo vệ seed order, legacy 5 seed, best-not-last, save/load, corrupt checkpoint, metadata,
scaler/mapping/schema, deterministic selection và deployment artifact. Kết quả cuối ở log 044.

**Giới hạn:** full 3 seed và preflight trên máy đích chưa chạy; chưa có deployment model thật;
chưa implement distributed aggregation nhiều máy.

**Commit đề xuất:** `feat: prepare portable global DL4J training and model selection`.

## 32. Workflow RTX 3090 có smoke, calibration batch và full 10 epoch

**Yêu cầu:** thay protocol batch 64/max 30 bằng chuỗi preflight → GPU smoke → calibration
64/256, 128/512, 256/1024 → chọn cấu hình → full 3 seed × tối đa 10 epoch.

**Thay đổi:** cấu hình tách train/validation batch; calibration mode dùng seed 42, 50 items,
một epoch; full global mặc định tối đa 10 epoch. Selector giữ RMSE bằng ngưỡng baseline +1%, rồi
ưu tiên sec/epoch, RMSE và train batch. Script một lệnh chạy đủ phase, lưu selection artifact và
copy provenance vào run full. Calibration không tạo deployment checkpoint; full vẫn lưu best model
từng seed và chọn deployment bằng validation Macro RMSE.

**Bất biến giữ nguyên:** scaler fit train-only, split 2013–2015/2016, tensor 56×30, H1–H7,
ResidualAR-MSE, LSTM 32, Adam 0,001, patience 5 và không dùng test 2017 để chọn.

**Kiểm chứng:** focused 14/14 PASS; full Maven 55/55 PASS; PowerShell selector integration và parser
PASS. Chỉ dùng fixture synthetic, không chạy smoke/calibration/full thật.

**Giới hạn:** chưa đo RTX 3090 nên khoảng calibration 10–30 phút, cấu hình thắng, runtime full và
deployment model vẫn chưa biết.

**Commit đề xuất:** `feat: add calibrated RTX 3090 global training workflow`.

## 33. Thêm Global Test 2017 evaluation-only từ checkpoint

**Yêu cầu:** đánh giá ba best-validation checkpoint của Full GlobalResidualAR trên Test 2017 mà không
train lại, không thay weights/checkpoint/deployment và không dùng test để chọn seed hay hyperparameter.

**Triển khai:** dataset global expose test samples; preprocessing có đường tạo window bằng scaler artifact
bất biến. Runner riêng kiểm tra dataset SHA-256, 50 scaler, metadata, best epoch 11/20/13 và deployment
seed 123 trước khi restore trực tiếp `model.zip`. Inference tái sử dụng rollout causal H1-H7 và cùng test
keys cho Weekly Naive. Kết quả được ghi mới dưới `test-evaluation/`, có metrics seed/item/horizon, toàn bộ
predictions, summary, HTML và manifest hash/runtime; thư mục đã tồn tại bị từ chối để tránh overwrite.

**Kiểm chứng:** TDD RED ban đầu do thiếu factory scaler artifact và test collection. Focused evaluation
4/4 PASS; full Maven 59/59 PASS, không failure/error/skip, BUILD SUCCESS 3:16. Regression kiểm tra split
2017 có 359 windows/item và 17.950 origins, weights/hash checkpoint synthetic bất biến, key Weekly/model
khớp, không duplicate và source runner không có API training/mutation. PowerShell parser PASS.

**Giới hạn:** chưa chạy command trên ba checkpoint thật theo yêu cầu dừng chờ xác nhận, nên chưa có
376.950 prediction hoặc Test metrics thực tế. Test synthetic chứng minh đường inference; lần chạy GPU
thật vẫn phải hoàn tất để xác nhận runtime và artifact đầu ra trên Full run.

**Commit đề xuất:** `feat: add checkpoint-only global test evaluation`.

## 34. Sửa Maven launcher cho Global Test evaluation-only

**Nguyên nhân:** `exec-maven-plugin` hard-code `com.dl4j.salesforecast.Main`; stack trace tái hiện
an toàn bằng `--invalid` đi vào `Main` và `ExperimentConfiguration.fromArgs` thay vì evaluation main.

**Sửa tối thiểu:** thêm property `exec.mainClass` mặc định là `Main`, cho plugin tham chiếu property,
và truyền override trước Maven goals bằng PowerShell argument array. Các command cũ không truyền
override vẫn chạy `Main`. Đồng thời bỏ lời gọi `ExperimentConfiguration.fromArgs` không dùng khỏi runner.

**Kiểm chứng:** TDD RED 1 failure đúng do POM chưa parameterize. Sau sửa, focused 5/5 và full 60/60
PASS; probe explicit main với `--invalid` dừng tại `GlobalTestEvaluationMain.java:10`; PowerShell parser
và `git diff --check` PASS. Không chạy Test 2017, không tạo output, không train và không sửa model.

**Commit đề xuất:** `fix: route test evaluation to its dedicated main class`.

## 35. Sửa lỗi HIGH bị nhận nhầm là horizon trong test report

**Nguyên nhân:** helper `Group` dùng `startsWith("H")` để nhận diện horizon. Tên demand tier `HIGH`
cũng bắt đầu bằng H, nên code gọi `Integer.parseInt("IGH")` sau khi ba checkpoint đã inference xong.

**Sửa:** chỉ parse ID khi tên khớp `H[1-7]`; LOW/MEDIUM/HIGH giữ ID horizon bằng 0. Thêm regression
test tái hiện chính xác lỗi `IGH` và xác nhận `H7` vẫn được nhận diện đúng. Không thay inference,
metrics, model, scaler, split hoặc checkpoint.

**Kiểm chứng:** RED 1 failure với `NumberFormatException: IGH`; focused 6/6 và full 61/61 PASS,
BUILD SUCCESS 1:59. Không chạy lại Test 2017. Partial FAILED manifest được archive có thể phục hồi,
không xóa; thư mục output chuẩn đã sẵn sàng cho command kế tiếp.

**Commit đề xuất:** `fix: distinguish HIGH tier from horizon labels`.
