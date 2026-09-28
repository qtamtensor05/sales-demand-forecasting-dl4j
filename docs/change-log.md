# Nhật ký yêu cầu

Các bước trước khi có nhật ký được tổng hợp tại [lịch sử triển khai](implementation-history.md).
Mục dưới đây bắt đầu quy trình ghi nhận sau từng yêu cầu; không dựng lại ngày/commit chưa biết.

## 001 — 2026-09-27 — Tài liệu tiến trình và skill hoàn tất yêu cầu

**Yêu cầu:** tạo `docs` mô tả từ đầu đến hiện tại theo thứ tự, lý do và kết quả;
thêm skill để cập nhật tài liệu và đề xuất tên commit sau mỗi yêu cầu.

**Lý do:** hỗ trợ báo cáo đồ án và giữ tài liệu đồng bộ với triển khai.

**Thay đổi:** thêm mục lục, lịch sử 9 bước, trạng thái pipeline và nhật ký;
thêm `.agents/skills/sales-project-progress/SKILL.md` cùng `AGENTS.md` để các phiên agent
đọc quy trình. Bổ sung liên kết docs vào README và hướng dẫn đóng góp.
Đã cài thêm bản skill vào `~/.codex/skills/sales-project-progress` để khám phá trong môi trường cá nhân;
bản trong repository là nguồn chuẩn.
Sửa hướng dẫn dữ liệu mẫu: CSV ngắn chỉ chạy analyzer, không đủ cho preprocessing mặc định.

**Kết quả:** tài liệu nêu đầy đủ dataset analysis, định dạng/xuất báo cáo, chuẩn bị GitHub,
time series analysis, preprocessing và chẩn đoán commit; phân biệt kết quả đã đạt với bước chưa làm.
Skill yêu cầu ghi bằng chứng và commit title; không tự commit/push.

**Kiểm tra:** đối chiếu code, báo cáo dataset, lịch sử Git và báo cáo Surefire hiện có
(8 tests đạt từ lần chạy preprocessing). Không chạy lại Java vì yêu cầu này chỉ thay tài liệu/quy trình.
Validator `quick_validate.py` xác nhận cả bản skill trong repo và bản cá nhân hợp lệ.
Đã kiểm tra 19 liên kết Markdown cục bộ trên 8 file, tất cả tồn tại; `git diff --check` không báo lỗi.

**Giới hạn:** quy trình cần agent đọc `AGENTS.md`/skill; không tự chạy khi người dùng sửa file thủ công trong IDE.

**Commit đề xuất:** `docs: document implementation progress and add completion workflow skill`

## 002 — 2026-09-27 — Baseline dự báo theo cùng thứ tuần trước

**Yêu cầu:** triển khai baseline sau preprocessing để có số đo tham chiếu trước LSTM.

**Lý do:** kiểm tra độ chính xác của quy tắc mùa vụ tuần và thiết lập MAE/RMSE cho đối chiếu công bằng với mô hình.

**Thay đổi:** thêm `WeeklyNaiveBaseline`, nối `Main` để dự báo sales(t) bằng sales(t−7)
trên mỗi raw target của test; tính MAE/RMSE theo horizon 1–7 và tổng hợp.
Ghi rõ protocol rolling origin, việc cửa sổ chồng lấn, kết quả và giới hạn trong README/docs.
Thêm 2 kiểm thử với giá trị sai số đã biết và trường hợp input history dưới 7 ngày.

**Kết quả đã chạy trên dataset:** 359 origin, 359 dự báo/horizon, tổng 2.513 điểm dự báo.
MAE/RMSE tổng hợp 5,2774/6,6393 sales. Horizon 1: 5,2312/6,6088; horizon 7: 5,3175/6,6636.

**Kiểm tra:** `mvn test exec:java` — 10 tests, 0 failures, 0 errors; BUILD SUCCESS.
Chạy được với `data/train.csv` thực tế; không sửa cách chia tập hay scaler.

**Giới hạn:** chỉ store 1 + item 1; dự báo cuốn chiếu dùng actual history; chưa có LSTM,
so sánh model hoặc đánh giá các chuỗi khác.

**Commit đề xuất:** `feat: add weekly naive sales baseline evaluation`

## 003 — 2026-09-27 — Tensor ND4J và LSTM dự báo 7 ngày

**Yêu cầu:** triển khai bước kế tiếp sau baseline, xây dựng và thử LSTM trên chuỗi store 1 + item 1.

**Lý do:** kiểm chứng tensor ND4J, huấn luyện DL4J và suy luận nhiều bước trực tiếp trước khi mở rộng đồ án.

**Thay đổi:** thêm `SalesLstmForecaster`; features `[batch, 1, 30]`, labels `[batch, 7, 30]`,
mask chỉ dùng loss tại time step cuối để học vector 7 ngày; mạng LSTM + RnnOutputLayer.
Epoch được chọn theo RMSE validation, rồi mới khôi phục tham số tốt nhất và đánh giá test.
Gọi LSTM từ `Main`, thêm test train hai epoch/kiểm tra shape, cập nhật README và docs.

**Kết quả trên dữ liệu thật:** 30 epoch, epoch được chọn 30, validation RMSE 8,1373;
test có 359 origin/2.513 forecast. LSTM MAE/RMSE 6,6032/8,5910, kém weekly naive
5,2774/6,6393. Horizon 7 RMSE 15,2724. Đây là kết quả khởi đầu, không phải mô hình tốt.

**Kiểm tra:** `mvn test` — 11 tests, 0 failures/errors; `mvn compile exec:java` BUILD SUCCESS.
Test ND4J huấn luyện mô hình nhỏ và xác nhận sequence output `[1, 7, 30]`.

**Giới hạn:** mới một chuỗi, cấu hình chưa tinh chỉnh, model chưa lưu/nạp; cần chẩn đoán kết quả.

**Commit đề xuất:** `feat: add multi-step DL4J LSTM forecaster`

## 004 — 2026-09-27 — Đề xuất thứ tự cải thiện LSTM

**Yêu cầu:** xác định bước nên làm sau lần train LSTM đầu tiên.

**Lý do:** LSTM đang có MAE/RMSE 6,6032/8,5910, kém baseline tuần 5,2774/6,6393;
horizon thứ 7 có RMSE cao 15,2724.

**Kết luận:** ưu tiên phân tích validation và phân phối dự báo theo horizon, lập baseline tuần
trên validation, sau đó tinh chỉnh learning rate/hidden units/patience chỉ bằng validation.
Không dùng lại test 2017 để chọn cấu hình. Khi báo cáo lần đánh giá cuối, nói rõ test 2017
đã được quan sát cho lần chạy thăm dò baseline/LSTM trước đó. Sau bước tuning mới tính chuyện lưu model.

**Thay đổi mã nguồn:** không có; đây là đề xuất kế hoạch. Cập nhật thứ tự bước kế tiếp trong
`docs/current-state.md`.

**Kiểm tra:** đối chiếu metric LSTM/baseline đã ghi nhận và trạng thái pipeline; không chạy test
vì không sửa code.

**Giới hạn:** cần xem đường RMSE validation theo epoch và sai số dự báo thực tế để xác định
horizon 7 lỗi do tối ưu hóa, normalization hoặc kiến trúc; metric hiện có chưa đủ chẩn đoán nguyên nhân.

**Commit đề xuất:** `docs: prioritize validation-based LSTM diagnostics`

## 005 — 2026-09-27 — Triển khai tuning validation và rà soát độ an toàn/độ chính xác

**Mục tiêu:** triển khai bước tiếp theo sau LSTM baseline: chẩn đoán lỗi theo horizon, thử cấu hình
chỉ trên validation, đánh giá mã nguồn và xử lý dependency có phiên bản dễ bị tổn thương.

**Lý do:** cấu hình LSTM đầu tiên có test MAE/RMSE 6,6032/8,5910, kém weekly naive 5,2774/6,6393;
chỉ metric tổng hợp chưa cho thấy horizon nào gây lỗi. Runtime tree cũng có Gson 2.8.0, Commons
Compress 1.21, Commons Net 3.1 và Commons Lang 3.11.

**Thay đổi:** `SalesLstmForecaster` có grid ba cấu hình, early-stopping patience, checkpoint RMSE
validation tốt nhất, metric và thống kê phân phối theo horizon. `fit` chỉ nhận train/validation;
test được đánh giá riêng sau search. `Main` in weekly baseline cho validation và test. Tăng kiểm tra
shape/dữ liệu hữu hạn/cấu hình trong forecaster, kiểm tra window và horizon ở baseline; analyzer đọc
UTF-8 và từ chối ID không dương/sales âm. `pom.xml` ghim Gson 2.8.9, Commons Compress 1.26.2,
Commons Net 3.9.0 và Commons Lang 3.18.0. Cập nhật README, trạng thái hiện tại và lịch sử.

**Kết quả quan sát:** với dataset thật, grid chọn 16 units, learning rate 0,001, epoch 30;
validation MAE/RMSE 6,0911/7,7108. Test MAE/RMSE 6,4400/8,1550 vẫn tệ hơn baseline
5,2774/6,6393. Ba ứng viên đều chạm giới hạn 30 epoch; patience 5 chưa dừng sớm, nên lần tìm này
chưa chứng minh hội tụ. Test được tính sau selection nhưng đã từng được xem trong bước trước.

**Tệp chính:** `pom.xml`, `Main.java`, `SalesLstmForecaster.java`, `WeeklyNaiveBaseline.java`,
hai analyzer, test forecaster/analyzer, `README.md`, `docs/current-state.md`,
`docs/implementation-history.md`.

**Kiểm tra:** `mvn test` — 11 tests, 0 failures/errors, BUILD SUCCESS sau khi ghim dependencies;
`mvn compile exec:java` trên CSV thật — BUILD SUCCESS; `mvn dependency:tree` xác nhận versions
Gson 2.8.9, Compress 1.26.2, Net 3.9.0, Lang 3.18.0. `git diff --check` và rà liên kết Markdown
được thực hiện trước khi hoàn tất.

**Giới hạn:** mới một chuỗi/seed, grid nhỏ và LSTM vẫn kém weekly baseline; cần mở rộng thí nghiệm
trên validation, không tinh chỉnh theo test. Việc xử lý các CVE đã biết không phải chứng nhận an toàn
toàn bộ dependency graph.

**Commit đề xuất:** `feat: tune LSTM on validation and harden dependencies`

## 006 — 2026-09-27 — Bổ sung causal features vào đầu vào LSTM

**Mục tiêu:** triển khai feature engineering cho đầu vào 30 ngày gồm sales, lag 7, rolling mean
7/14 ngày và day-of-week, rồi đo tác động trên validation.

**Lý do:** LSTM nhiều output hiện tại dùng một kênh sales và vẫn kém baseline tuần. Các feature
tuần và mức bán gần đây có thể cung cấp tín hiệu mà mô hình chưa nhận trực tiếp.

**Thay đổi:** `SalesPreprocessor` tạo feature vector sáu kênh cho từng ngày input: sales chuẩn hóa,
sales lag-7, rolling mean 7, rolling mean 14, day-of-week sin/cos. Mọi thống kê sales tại ngày t
chỉ dùng quan sát đến t; rolling window bao gồm t. Scaler vẫn fit trên train, không clip validation/
test. Cửa sổ train bắt đầu sau 14 ngày feature history đầy đủ; không điền giá trị giả cho warm-up.
`SalesLstmForecaster` nhận tensor `[batch, 6, 30]`; horizon output 7 ngày, split dates và target
labels không đổi. Bổ sung kiểm thử giá trị feature, shape và trường hợp chuỗi constant.

**Kết quả:** train 1.046 windows (giảm 13 cửa sổ đầu), validation 360, test 359. Grid chọn 32
units, learning rate 0,001, epoch 30 theo validation RMSE 6,9524 (MAE 5,5388). Test LSTM mới
MAE/RMSE 5,8843/7,3887; so với LSTM trước 6,4400/8,1550 đã giảm sai số, nhưng vẫn kém baseline
tuần 5,2774/6,6393. Horizon 3 có validation RMSE cao nhất 7,8159; horizon 7 có test RMSE cao nhất
8,2492. Không chọn model bằng test.

**Tệp chính:** `SalesPreprocessor.java`, `SalesLstmForecaster.java`, test preprocessor,
test forecaster, test weekly baseline, `README.md`, `docs/current-state.md`,
`docs/implementation-history.md`.

**Kiểm tra:** `mvn test` — 11 tests, 0 failures/errors, BUILD SUCCESS. Có một lượt test đầu phát
hiện expectation cũ về số cửa sổ và synthetic baseline chưa đủ 14 ngày warm-up; fixture được cập
nhật theo chính sách feature history, lượt cuối pass. `mvn -B compile exec:java` trên CSV thật —
BUILD SUCCESS, search validation và báo cáo test hoàn tất. `git diff --check` và kiểm tra link Markdown
được thực hiện cuối.

**Giới hạn:** chỉ đo một store-item và một seed; LSTM vẫn chưa vượt baseline. Feature engineering
chưa chứng minh cải thiện khái quát; tiếp theo so sánh kiến trúc khác bằng validation và mở rộng
nhiều chuỗi/seed. Test 2017 đã từng được quan sát từ trước.

**Commit đề xuất:** `feat: add causal time-series features to LSTM inputs`

## 007 — 2026-09-27 — So sánh forecast direct và autoregressive

**Mục tiêu:** giữ direct 7-output làm baseline neural, thêm autoregressive multi-step, so sánh cả hai
với weekly naive trên validation.

**Lý do:** direct model hiện có đầu vào nhiều feature nhưng dự báo 7 ngày đồng thời; cần biết rollout
từng ngày, trong đó prediction trước được đưa lại làm input, có cải thiện chất lượng không.

**Thay đổi:** thêm `AutoregressiveLstmForecaster`: train target một ngày kế tiếp; suy luận lặp 7 bước,
cập nhật prediction vào lag/rolling features và lịch weekday. Chọn checkpoint theo recursive RMSE
validation với early stopping. AR dùng cùng seed, hidden units, learning rate của direct winner để
so sánh kiến trúc có kiểm soát. `Main` in metric theo horizon và bảng validation cho weekly naive,
direct và autoregressive; không đọc test trong lượt so sánh này. Bổ sung smoke test cho fit/rollout.

**Kết quả:** validation weekly naive MAE/RMSE 5,3032/6,6216; direct LSTM 5,5388/6,9524; AR LSTM
5,4384/6,7275 (mỗi phương pháp 2.520 dự báo chồng lấn). AR tốt hơn direct 0,2249 RMSE nhưng chưa
vượt weekly naive. AR chọn epoch 15 và dừng tại epoch20 với patience5. Đánh giá test mới không chạy;
test 2017 đã được xem trong lượt trước.

**Tệp chính:** `AutoregressiveLstmForecaster.java`, `Main.java`, test LSTM, `README.md`,
`docs/current-state.md`, `docs/implementation-history.md`.

**Kiểm tra:** `mvn test` — 11 tests, 0 failures/errors; `mvn -B compile exec:java` trên dữ liệu thật
— BUILD SUCCESS sau 5:07 phút; so sánh hoàn tất, không tính metric test. `git diff --check` và
kiểm tra liên kết Markdown được thực hiện sau cập nhật tài liệu.

**Giới hạn:** một seed, một store-item, AR chưa được tune grid riêng mà mượn config direct; weekly
naive là phương pháp có RMSE thấp nhất. Bước tiếp theo là nhiều seed cho direct và AR trên validation,
rồi cân nhắc mở rộng store-item.

**Commit đề xuất:** `feat: compare autoregressive and direct LSTM forecasts`
## 008 — 2026-09-28 — Chạy so sánh LSTM qua nhiều seed

**Mục tiêu:** chạy Direct LSTM và Autoregressive LSTM với seed 42, 123, 2026, 7, 99; báo cáo
mean ± std MAE/RMSE validation và weekly naive.

**Lý do:** kết quả kiến trúc ở bước trước chỉ dựa trên một seed, nên chưa cho biết chênh lệch có
ổn định qua các lần khởi tạo trọng số hay không.

**Thay đổi:** thêm overload seed tường minh cho hai forecaster; thêm `ExperimentRunner` cố định
32 units, learning rate 0,001, tối đa 30 epoch và patience 5 để chạy cả hai mô hình theo từng seed.
Runner ghi từng kết quả, mean và độ lệch chuẩn mẫu (n−1), cùng weekly naive; không tính test.
`Main` gọi runner. Thêm kiểm thử sample standard deviation/đầu vào và assertion seed. Cập nhật
README, trạng thái hiện tại và lịch sử triển khai.

**Kết quả:** Direct MAE 4,8238 ± 0,2701, RMSE 6,0980 ± 0,3474; AR MAE 4,8502 ± 0,4442,
RMSE 6,0287 ± 0,5050; weekly naive MAE/RMSE 5,3032/6,6216. AR mean RMSE thấp hơn Direct 0,0693,
nhưng chỉ thắng 2/5 seed (Direct thắng 3/5) và độ lệch chuẩn AR cao hơn. Cả hai có mean RMSE thấp
hơn weekly naive. Kết quả chỉ trên một chuỗi validation, không phải kiểm định ý nghĩa; chưa xác nhận
lợi thế AR ổn định và không tính metric test.

**Tệp chính:** `ExperimentRunner.java`, `SalesLstmForecaster.java`,
`AutoregressiveLstmForecaster.java`, `Main.java`, `ExperimentRunnerTest.java`,
`SalesLstmForecasterTest.java`, `README.md`, `docs/current-state.md`,
`docs/implementation-history.md`.

**Kiểm tra:** `mvn -B -DskipTests compile` — BUILD SUCCESS; `mvn -B test` — 13 tests, 0 lỗi;
`mvn -B compile exec:java` trên `data/train.csv` — đủ 10 lượt LSTM, `BUILD SUCCESS`, tổng thời gian
15:17 phút. `git diff --check` và kiểm tra liên kết Markdown sau cập nhật tài liệu.

**Giới hạn:** một cặp store-item; năm seed; validation chọn checkpoint; AR dùng units/rate của Direct;
weekly baseline cũng được tính trên các cửa sổ validation cuốn chiếu chồng lấn. Mở rộng sang nhiều
chuỗi là bước tiếp theo; test 2017 từng được xem trước đây và không được dùng trong lượt này.

**Commit đề xuất:** `feat: compare LSTM strategies across random seeds`

## 009 — 2026-09-28 — Mở rộng thí nghiệm sang chuỗi đại diện

**Mục tiêu:** đánh giá Weekly Naive, Direct LSTM và Autoregressive LSTM trên 10–20 cặp store-item
đại diện theo mức doanh số và tổng hợp macro theo chuỗi.

**Lý do:** đánh giá một chuỗi là giới hạn lớn; scale khác nhau giữa các sản phẩm khiến gộp mọi forecast
point có thể làm nhóm doanh số lớn chi phối kết luận.

**Thay đổi:** thêm `MultiSeriesExperimentRunner`, phân tầng theo mean sales/ngày của train 2013–2015,
lọc profile đủ 1.095 ngày train duy nhất, lấy 4 chuỗi từ mỗi tertile LOW/MEDIUM/HIGH (12 tổng cộng).
Mỗi chuỗi có preprocessing/scaler riêng (scaler fit train-only), dùng chung cấu hình 32 units,
learning rate 0,001, tối đa 30 epoch, patience 5, cùng 5 seed. Macro tính mean metric qua seed trong
từng chuỗi trước, rồi macro-average không trọng số giữa chuỗi; sample std báo độ phân tán giữa 12
chuỗi. Main xuất `output/multi-series-validation.txt`. Thêm chế độ phân tầng được kiểm thử và bổ sung
`TimeSeriesAnalyzer.analyzeQuietly` để tái sử dụng dữ liệu mà không in 12 báo cáo chẩn đoán dài.

**Kết quả:** 12 chuỗi × 5 seed × 2 neural models = 120 fits trên validation. Macro Weekly naive
MAE/RMSE 8,7365 ± 2,4252 / 11,1435 ± 3,1713; Direct 10,3217 ± 4,1082 / 13,0076 ± 5,1836;
Autoregressive 9,7157 ± 3,5308 / 12,1396 ± 4,4385. AR tốt hơn Direct về macro nhưng Weekly naive
vẫn thấp hơn cả hai; AR có macro RMSE thấp hơn Direct tại 11/12 chuỗi. Không dùng test.

**Tệp chính:** `MultiSeriesExperimentRunner.java`, `ExperimentRunner.java`, `Main.java`,
`TimeSeriesAnalyzer.java`, `MultiSeriesExperimentRunnerTest.java`, `README.md`,
`docs/current-state.md`, `docs/implementation-history.md`.

**Kiểm tra:** `mvn test` — 15 tests, 0 lỗi; `mvn -B compile exec:java` — 120 fit, `BUILD SUCCESS`,
tổng thời gian 2:05 giờ. `output/multi-series-validation.txt` có 12 dòng chuỗi và macro. `git diff --check`
và kiểm tra liên kết Markdown sau cập nhật tài liệu.

**Giới hạn:** mới 12/500 chuỗi; std mô tả độ phân tán giữa chuỗi, không phải CI; cấu hình fixed từ
thí nghiệm trước, validation dùng chọn epoch. Weekly naive vẫn là model tốt nhất theo macro. Bước sau
nên phân tích theo horizon/tier trước khi sửa mô hình. Test 2017 đã từng được xem và không được đọc ở đây.

**Commit đề xuất:** `feat: evaluate forecasts across representative series`
