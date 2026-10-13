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

## 010 - 2026-10-04 - Chỉ đọc log huấn luyện sau khi tiến trình hoàn tất

**Mục tiêu:** bổ sung quy tắc để agent không liên tục đọc kết quả từng epoch trong các lượt huấn
luyện hoặc thí nghiệm dài, mà đọc và tổng hợp log sau khi có kết quả cuối cùng.

**Lý do:** đọc log lặp lại trong lúc tiến trình khỏe mạnh tạo thao tác không cần thiết và dễ khiến
kết quả trung gian bị hiểu như kết quả hoàn tất; agent vẫn cần giữ đủ log để kiểm chứng sau cùng.

**Thay đổi:** thêm mục `Long-running training` vào
`.agents/skills/sales-project-progress/SKILL.md`. Quy tắc yêu cầu lưu đầy đủ stdout/stderr, chờ
tiến trình kết thúc, chỉ kiểm tra liveness/exit khi công cụ bắt buộc và không đọc log epoch đang tăng.
Sau khi kết thúc, agent đọc log một lần để lấy exit code, metric cuối, cấu hình được chọn, thời lượng,
cảnh báo và lỗi. Cho phép đọc sớm khi tiến trình lỗi, có dấu hiệu treo, cần chẩn đoán hoặc người dùng
yêu cầu live. Cập nhật `docs/README.md`, `docs/current-state.md` và lịch sử triển khai để phản ánh
quy trình này.

**Tệp ảnh hưởng:** `.agents/skills/sales-project-progress/SKILL.md`, `docs/README.md`,
`docs/current-state.md`, `docs/implementation-history.md`, `docs/change-log.md`.

**Kết quả:** skill dự án đã mô tả rõ cơ chế chờ yên lặng và đọc kết quả cuối, đồng thời vẫn giữ
khả năng theo dõi trạng thái tiến trình và chẩn đoán lỗi. Không thay đổi code Java, dữ liệu,
normalization hay split theo thời gian.

**Kiểm tra:** đã gọi validator của `skill-creator` nhưng validator không khởi động do môi trường
Python thiếu mô-đun `yaml`; kiểm tra thủ công xác nhận frontmatter có `name`, `description` và tên
`sales-project-progress` khớp thư mục. Kiểm tra liên kết Markdown trong tài liệu và
`git diff --check`. Không chạy Maven vì thay đổi chỉ liên quan hướng dẫn agent và tài liệu.

**Giới hạn:** validator tự động chưa chạy hoàn chỉnh do thiếu PyYAML. Quy tắc phụ thuộc agent/công
cụ thực thi tuân thủ; nếu công cụ có timeout, agent vẫn cần kiểm tra trạng thái nhưng không đọc log
epoch. Chương trình Java vẫn ghi log như trước.

**Commit đề xuất:** `docs: defer training log review until completion`

## 011 - 2026-10-04 - Khảo sát nguồn agent skill phổ biến cho dự án

**Mục tiêu:** tìm các nguồn agent skill được sử dụng rộng rãi và đánh giá mức phù hợp với dự án
Java 11, Maven, DL4J và thí nghiệm dự báo chuỗi thời gian này.

**Lý do:** skill phổ biến thường hướng đến frontend hoặc quy trình phát triển tổng quát; cài toàn bộ
có thể làm tăng context, trùng quy tắc và xung đột với workflow riêng của repository. Cần tách mức
độ phổ biến khỏi mức độ phù hợp kỹ thuật.

**Phát hiện:** tại thời điểm khảo sát, các nguồn lớn gồm `obra/superpowers` (khoảng 294,9 nghìn
GitHub stars), `anthropics/skills` (khoảng 176 nghìn), `addyosmani/agent-skills` (khoảng 95,2 nghìn),
`vercel-labs/skills` (khoảng 33,1 nghìn), `vercel-labs/agent-skills` (khoảng 31,9 nghìn) và
`trailofbits/skills` (khoảng 7 nghìn). Star chỉ là tín hiệu adoption, không bảo đảm chất lượng hoặc
độ an toàn của từng skill. Repository `openai/skills` cũ đã được đánh dấu deprecated; với Codex nên
ưu tiên tài liệu/plugin repository hiện hành của OpenAI.

Nguồn phù hợp nhất để chọn lọc cho dự án là `addyosmani/agent-skills`, đặc biệt ba workflow
`debugging-and-error-recovery`, `test-driven-development` và `code-review-and-quality`. Chúng khớp
với Maven tests, sửa lỗi và quality gate mà không phụ thuộc frontend. `obra/superpowers` có các ý
tưởng tốt về systematic debugging và verification-before-completion nhưng toàn bộ pack khá áp đặt,
có thể chồng lên `sales-project-progress`, quy tắc không tự commit và chính sách agent hiện có.
`trailofbits/skills` hữu ích khi thực hiện security audit chuyên sâu. `arvindand/agent-skills` có
`maven-tools` phù hợp kiểm tra dependency JVM nhưng yêu cầu Maven Tools MCP và chưa có bằng chứng
adoption ngang các bộ lớn. `vercel-labs/agent-skills` chủ yếu phục vụ React/Next.js nên không phù hợp
trực tiếp với pipeline DL4J hiện tại.

**Khuyến nghị:** dùng `skills.sh`/`vercel-labs/skills` làm công cụ khám phá và xem source trước khi
cài. Nếu bổ sung, nên nhập hoặc điều chỉnh từng skill theo thứ tự: debugging, TDD, code review; sau
đó mới cân nhắc Maven dependency và security audit. Không cài toàn bộ pack, không lấy star làm tiêu
chí duy nhất, và phải rà soát script, quyền, network, license cùng quy tắc commit trước khi đưa vào
`.agents/skills/`.

**Tệp ảnh hưởng:** chỉ `docs/change-log.md`; không thay đổi code, cấu hình, dependency hay skill đang
hoạt động và không cài nguồn bên thứ ba.

**Kiểm tra:** đối chiếu README/repository gốc, tài liệu OpenAI hiện hành và số liệu GitHub công khai;
chạy kiểm tra liên kết Markdown cục bộ và `git diff --check`. Không chạy Maven vì đây là yêu cầu
điều tra, không có thay đổi hành vi chương trình.

**Giới hạn:** GitHub stars và lượt cài thay đổi theo thời gian; một số marketplace tổng hợp source
từ repository bên ngoài nên cần kiểm tra lại commit/license trước mỗi lần cài. Chưa chạy benchmark
đối chứng các skill trên repository này và chưa audit bảo mật toàn bộ script của nguồn đề xuất.

**Commit đề xuất:** `docs: evaluate agent skill sources for DL4J workflow`

## 012 - 2026-10-04 - Thêm ba skill kỹ thuật project-native

**Mục tiêu:** bổ sung nội dung skill debugging, TDD và code review đã đề xuất sau khảo sát, phù hợp
với dự án Java 11/Maven/DL4J hiện tại.

**Lý do:** các bộ skill phổ biến cung cấp workflow hữu ích nhưng bản đầy đủ có nhiều quy tắc
frontend, commit, orchestration hoặc lifecycle không cần thiết và có thể xung đột với
`sales-project-progress`. Phiên bản project-native giữ phần có giá trị và mã hóa trực tiếp các rủi
ro leakage, chronological split, tensor/mask, metric và lượt training dài của repository.

**Thay đổi:** tạo `.agents/skills/debugging-and-error-recovery/SKILL.md` để tái hiện và định vị
nguyên nhân gốc trước khi sửa; tạo `.agents/skills/test-driven-development/SKILL.md` để áp dụng
red-green-refactor bằng JUnit 5 cho thay đổi hành vi; tạo
`.agents/skills/code-review-and-quality/SKILL.md` để review findings theo correctness, leakage,
maintainability, security và performance. Ba skill có phạm vi kích hoạt riêng, giữ Java 11,
train-only normalization, chronological split, validation-only selection và quiet-log workflow.
Chúng không tự cho phép sửa code, chạy training dài, nâng dependency, commit hoặc push.

**Tệp ảnh hưởng:** ba `SKILL.md` mới trong `.agents/skills/`, `docs/README.md`,
`docs/current-state.md`, `docs/implementation-history.md` và `docs/change-log.md`.

**Kết quả:** repository có ba workflow kỹ thuật tự khám phá, ngắn gọn và không phụ thuộc MCP hoặc
script ngoài. Không thay đổi source Java, Maven dependency, dữ liệu, normalization, split hoặc kết
quả thí nghiệm.

**Kiểm tra:** đã gọi validator `skill-creator` cho từng skill nhưng cả ba lượt đều dừng trước khi
đọc skill vì Python thiếu mô-đun `yaml`. Kiểm tra thủ công frontmatter, tên thư mục và scaffold
placeholder; kiểm tra liên kết Markdown cùng `git diff --check`. Không chạy Maven vì không có thay
đổi hành vi chương trình.

**Giới hạn:** chưa đánh giá hành vi trong một phiên Codex mới; validator tự động chưa chạy hoàn
chỉnh do thiếu PyYAML. Các workflow cần agent tuân thủ và không thay thế review/test thực tế.

**Commit đề xuất:** `feat: add project-native engineering agent skills`

## 013 - 2026-10-10 - Kiểm tra trạng thái và xác định bước triển khai tiếp theo

**Mục tiêu:** kiểm tra tình trạng hiện tại của repository và đề xuất bước triển khai kế tiếp dựa
trên bằng chứng đang có, không coi số liệu lịch sử là một lượt thí nghiệm mới.

**Lý do:** pipeline đã có baseline, Direct LSTM, Autoregressive LSTM và đánh giá 12 chuỗi, nhưng
weekly naive vẫn thắng macro. Cần xác nhận code/test/artifact hiện tại và thu hẹp nguyên nhân trước
khi tiếp tục thay đổi mô hình hoặc chạy lại 120 lượt huấn luyện.

**Phát hiện:** `master` sạch và đồng bộ với `origin/master`; môi trường hiện dùng OpenJDK 11.0.32.1
và Maven 3.9.16. Dataset `data/train.csv` cùng hai báo cáo cục bộ vẫn tồn tại. Báo cáo multi-series
gần nhất vẫn chứa đủ 12 chuỗi và macro validation: weekly naive RMSE 11,1435, Direct 13,0076,
Autoregressive 12,1396. Quan sát từng chuỗi cho thấy neural model còn cạnh tranh ở LOW nhưng thua
baseline rõ hơn tại MEDIUM/HIGH. Báo cáo chưa tổng hợp theo tier, horizon 1–7 hoặc độ phân tán seed
cho từng chuỗi, nên chưa đủ bằng chứng để chọn một thay đổi kiến trúc cụ thể.

**Đề xuất triển khai:** ưu tiên bổ sung breakdown validation theo tier, horizon và seed vào
`MultiSeriesExperimentRunner`, kèm JUnit cho phép tổng hợp/định dạng báo cáo. Giữ nguyên scaler
fit train-only, chronological split và model selection chỉ trên validation. Sau khi xác định nhóm
và horizon gây lỗi, mới thử một giả thuyết mô hình có kiểm soát; chưa mở rộng 500 chuỗi và không
dùng test 2017 để chọn giải pháp ở bước chẩn đoán.

**Tệp ảnh hưởng:** `docs/current-state.md` và `docs/change-log.md`; không thay đổi source Java,
dependency, dữ liệu, normalization hay ranh giới thời gian.

**Kiểm tra:** `mvn -B test` đạt 15 tests, 0 failures/errors/skips và `BUILD SUCCESS` trong 32,142
giây ngày 2026-10-10; kiểm tra artifact/report cục bộ, liên kết Markdown và `git diff --check`.

**Giới hạn:** không chạy lại thí nghiệm 120 fits; các metric mô hình là kết quả báo cáo cục bộ từ
lượt trước, không phải metric tái tạo trong yêu cầu này. Chỉ 12/500 chuỗi được đánh giá, chưa có
bootstrap/CI và test 2017 không còn là holdout chưa từng quan sát.

**Commit đề xuất:** `docs: record project status and next implementation step`

## 014 - 2026-10-10 - Triển khai hệ thống phân tích lỗi validation

**Mục tiêu:** thống kê lỗi theo nhóm doanh số, horizon và seed từ kết quả validation, không đổi
kiến trúc, không huấn luyện thêm trong yêu cầu này và không dùng test 2017 để lựa chọn.

**Lý do:** báo cáo cũ chỉ có metric tổng hợp từng chuỗi đã bình quân năm seed. Nó đủ để tính macro
theo LOW/MEDIUM/HIGH và số chuỗi thắng baseline, nhưng không đủ để tái dựng horizon hoặc phân tán
seed một cách đúng đắn.

**Thay đổi:** thêm `ErrorAnalysisRunner.java`; giữ Direct `ForecastMetrics` đầy đủ trong mỗi
`ExperimentRunner.SeedResult`; tích hợp `Main` để ghi `output/error-analysis-validation.txt` sau
lượt multi-series. Runner báo macro MAE/RMSE theo tier và horizon, từng seed với mean ± sample std,
phần trăm cải thiện so với weekly naive và số chuỗi thắng baseline theo RMSE. Thêm ba JUnit dùng
dữ liệu synthetic để kiểm tra metric, macro-average, sample std, grouping, improvement và format.
Không sửa forecaster, preprocessing, scaler, split, causal feature, model selection hoặc dependency.

**Kết quả thực sự quan sát:** artifact cũ cho thấy HIGH có RMSE lớn nhất: weekly 14,6811, Direct
18,7081, AR 17,0007. LOW là nhóm duy nhất hai LSTM cải thiện macro RMSE (+4,25% Direct, +5,54% AR);
MEDIUM lần lượt -17,39%/-10,10%, HIGH -27,43%/-15,80%. Direct thắng baseline ở 3/12 chuỗi và AR
ở 4/12. Horizon và seed gây lỗi lớn nhất chưa thể xác định vì artifact cũ không lưu breakdown;
không suy diễn từ macro. Báo cáo cục bộ hiện ghi rõ coverage này.

**Tệp ảnh hưởng:** `ErrorAnalysisRunner.java`, `ExperimentRunner.java`, `Main.java`,
`ErrorAnalysisRunnerTest.java`, `README.md`, `docs/current-state.md`,
`docs/implementation-history.md`, `docs/change-log.md`; báo cáo local trong `output/` bị Git ignore.

**Kiểm tra:** bước red `mvn -B -Dtest=ErrorAnalysisRunnerTest test` thất bại đúng vì chưa có class;
sau triển khai focused suite đạt 3/3. `mvn -B test` đạt 18 tests, 0 failures/errors/skips và
`BUILD SUCCESS` trong 45,003 giây. Kiểm tra liên kết Markdown và `git diff --check` sau cập nhật.

**Giới hạn:** chưa chạy lại 120 fits; lượt trước mất khoảng 2 giờ 05 phút. Vì vậy report hiện tại
chỉ có kết quả tier/win có thể tái sử dụng, còn horizon/seed được đánh dấu unavailable. Lần chạy
mặc định tiếp theo sẽ xuất đủ các phần này. Test 2017 không được truy cập.

**Giả thuyết tiếp theo:** MSE trên sales tuyệt đối có thể làm mức/đỉnh doanh số cao chi phối học,
phù hợp với khoảng cách xấu dần từ LOW sang HIGH. Sau khi breakdown horizon xác nhận, thử target
tương đối hoặc biến đổi ổn định phương sai fit train-only và đánh giá bằng cùng validation protocol.

**Commit đề xuất:** `feat: add validation error analysis reporting`

## 015 - 2026-10-10 - Xác nhận lệnh chạy lại multi-series fit

**Mục tiêu:** cung cấp lệnh PowerShell chính xác để chạy lại fit trên train và sinh báo cáo
validation/error-analysis theo entry point hiện tại.

**Phát hiện:** `Main` đọc `data/train.csv`, chạy `MultiSeriesExperimentRunner` với 12 chuỗi × 5
seed × 2 LSTM, chọn checkpoint chỉ bằng validation rồi ghi `output/multi-series-validation.txt`
và `output/error-analysis-validation.txt`. Lệnh Maven hiện hành là `mvn -B compile exec:java`.
Vì đây là 120 fits và lượt tương đương trước mất khoảng 2 giờ 05 phút, stdout/stderr nên được lưu
trọn vào một log ổn định và chỉ đọc sau khi tiến trình kết thúc.

**Tệp ảnh hưởng:** chỉ `docs/change-log.md`; không thay đổi code, cấu hình, dữ liệu hoặc kết quả.

**Kiểm tra:** đối chiếu `Main.java`, README và cấu hình Maven hiện tại; kiểm tra liên kết Markdown
cục bộ cùng `git diff --check`. Không chạy Maven/training vì người dùng chỉ hỏi câu lệnh.

**Giới hạn:** lệnh sẽ ghi đè các báo cáo hiện có khi hoàn tất. Nó cần `data/train.csv`, Java 11,
Maven và khoảng thời gian/tài nguyên tương ứng; không nên đóng terminal giữa chừng.

**Commit đề xuất:** `docs: record full validation rerun command`

## 016 - 2026-10-10 - Xác nhận backend huấn luyện hiện tại

**Mục tiêu:** xác định lượt multi-series fit hiện chạy trên CPU hay GPU.

**Phát hiện:** `pom.xml` khai báo `org.nd4j:nd4j-native-platform:1.0.0-M2.1`, là backend native
CPU. Log kiểm thử gần nhất cũng ghi `Loaded [CpuBackend] backend`, `Backend used: [CPU]` và BLAS
vendor `OPENBLAS`. Vì vậy lệnh `mvn -B compile exec:java` hiện không dùng GPU, kể cả khi máy có
GPU NVIDIA.

**Tệp ảnh hưởng:** chỉ `docs/change-log.md`; không thay đổi dependency, code hoặc cấu hình runtime.

**Kiểm tra:** đối chiếu trực tiếp `pom.xml` và report Surefire hiện có; chạy kiểm tra liên kết
Markdown cục bộ và `git diff --check`. Không chạy lại training hoặc Maven.

**Giới hạn:** chưa khảo sát driver/CUDA/GPU của máy vì backend hiện tại đã quyết định chạy CPU.
Muốn chuyển sang GPU cần thay backend ND4J bằng artifact CUDA tương thích, kiểm tra driver/CUDA và
chạy smoke test trước; đây là thay đổi cấu hình riêng, chưa được thực hiện.

**Commit đề xuất:** `docs: clarify current CPU training backend`

## 017 - 2026-10-10 - Chuyển backend GPU, đang thiếu CUDA runtime 11.6

**Mục tiêu:** chuyển ND4J từ CPU sang GPU NVIDIA cho các lượt fit DL4J.

**Lý do:** máy có NVIDIA GeForce RTX 4050 6 GB; backend cũ `nd4j-native-platform` chỉ sử dụng CPU.

**Thay đổi đang thực hiện:** thay dependency bằng `nd4j-cuda-11.6-platform` cùng phiên bản
DL4J/ND4J `1.0.0-M2.1`; thêm `BackendConfigurationTest` yêu cầu backend được nạp có tên CUDA.

**Kết quả kiểm tra:** bước red trên cấu hình cũ thất bại đúng với `CpuBackend`. Sau khi đổi
dependency, Maven phân giải được CUDA 11.6 artifacts nhưng smoke test dừng ở `jnicudart.dll: Can't
find dependent libraries`; `JCublasBackend` bị đánh dấu unavailable và không có backend thay thế.
`nvidia-smi` nhận RTX 4050 và driver 617.42/CUDA UMD 13.4, nhưng `nvcc`, `cudart64_110.dll`,
`cublas64_11.dll` và thư mục CUDA Toolkit không tồn tại. Do đó cấu hình repository đã chuyển sang
GPU nhưng môi trường chưa thể chạy.

**Tệp ảnh hưởng:** `pom.xml`, `BackendConfigurationTest.java`, `docs/change-log.md`. Không thay đổi
forecaster, preprocessing, normalization, split thời gian hoặc model-selection protocol.

**Giới hạn và trạng thái:** yêu cầu chưa hoàn tất. Cần cài CUDA Toolkit/runtime 11.6 tương thích
ở cấp hệ thống rồi chạy lại smoke test và toàn bộ suite. Cài đặt này tải dung lượng lớn, chấp nhận
CUDA EULA và có thể cần quyền quản trị; chưa tự thực hiện khi chưa có xác nhận của người dùng.

**Commit đề xuất sau khi kiểm chứng:** `build: switch ND4J training backend to CUDA`

## 018 - 2026-10-10 - Hướng dẫn cài CUDA Toolkit 11.6 thủ công

**Mục tiêu:** cung cấp quy trình để người dùng tự cài runtime tương thích với
`nd4j-cuda-11.6-platform`, sau đó xác nhận DL4J thực sự nạp GPU backend.

**Hướng dẫn:** dùng CUDA Toolkit 11.6 Update 2 cho Windows x86_64 từ NVIDIA archive; chọn Custom,
giữ CUDA Runtime/Development/Libraries và bỏ chọn NVIDIA display driver để tránh thay driver
617.42 mới hơn đang hoạt động. Sau cài đặt, mở terminal mới, xác nhận `nvcc --version`,
`cudart64_110.dll` và `cublas64_11.dll`, rồi chạy focused Maven test trước toàn suite.

**Điều chỉnh:** assertion smoke test nhận diện tên backend thực của ND4J là `JCublasBackend`
(`jcublas`) thay vì tìm chuỗi `cuda` trong class name. Đây là sửa điều kiện kiểm tra, chưa phải bằng
chứng GPU đã chạy vì CUDA Toolkit vẫn chưa được cài trong môi trường hiện tại.

**Tệp ảnh hưởng:** `BackendConfigurationTest.java` và `docs/change-log.md`; không cài phần mềm,
không chạy training, không đổi pipeline dữ liệu hoặc model.

**Kiểm tra:** đối chiếu backend Maven hiện tại và hướng dẫn NVIDIA CUDA 11.6 cho Windows; chạy
kiểm tra liên kết Markdown cục bộ cùng `git diff --check`. Không chạy lại smoke test vì runtime
11.6 vẫn đang thiếu và lỗi đã được xác định ở lượt trước.

**Giới hạn:** cuDNN là tối ưu bổ sung, không cần cho bước xác nhận `JCublasBackend` ban đầu. Nếu
installer yêu cầu khởi động lại hoặc terminal cũ chưa nhận PATH, phải hoàn tất việc đó trước khi
chạy Maven. Trạng thái chuyển GPU chỉ hoàn tất sau khi smoke test và toàn suite đạt.

**Commit đề xuất sau khi kiểm chứng:** `build: switch ND4J training backend to CUDA`

## 019 - 2026-10-10 - Đánh giá kỳ vọng tăng tốc khi dùng CUDA

**Mục tiêu:** làm rõ chuyển sang CUDA có chắc chắn làm lượt training nhanh hơn hay không.

**Phát hiện:** GPU có khả năng tăng tốc phép toán LSTM, nhưng mức tăng của dự án chưa được đo và
không thể bảo đảm. Mỗi model hiện nhỏ (32 hidden units, khoảng 1.046 train windows, sequence 30,
6 features), trong khi 120 fits chạy tuần tự. Direct/AR còn chấm validation sau mỗi epoch; AR có
rollout và tạo lại feature bằng Java, nên thời gian không nằm hoàn toàn trong kernel GPU. Với batch
nhỏ/mô hình nhỏ, chi phí khởi tạo CUDA, truyền dữ liệu và validation có thể làm lợi ích thấp, thậm
chí chậm hơn CPU ở smoke workload.

**Khuyến nghị kiểm chứng:** sau khi CUDA smoke test đạt, đo cùng một chuỗi, seed, epoch và dữ liệu
trên CPU/GPU; so sánh wall-clock sau warm-up, đồng thời xác nhận metric không đổi ngoài sai số số
học hợp lý. Chỉ dùng kết quả đo đó để ước lượng thời gian 120 fits.

**Tệp ảnh hưởng:** chỉ `docs/change-log.md`; không thay đổi code, dependency hoặc chạy benchmark.

**Giới hạn:** CUDA backend hiện chưa chạy được vì thiếu runtime 11.6, nên chưa có số liệu speedup
thực tế trên RTX 4050. Hiệu năng còn phụ thuộc compatibility CUDA 11.6 với GPU, batch/model size,
VRAM và phần Java/validation không được GPU hóa.

**Commit đề xuất:** `docs: clarify expected CUDA training speedup`

## 020 - 2026-10-10 - Xác nhận CUDA Toolkit và hoàn tất GPU backend

**Mục tiêu:** chẩn đoán vì sao terminal không nhận `nvcc`/CUDA DLL sau cài đặt và xác nhận backend
GPU hoạt động thực sự.

**Nguyên nhân:** CUDA Toolkit 11.6 đã được cài đầy đủ và Machine PATH đã có thư mục `v11.6\bin`,
nhưng terminal/VS Code hiện tại được mở trước khi installer cập nhật biến môi trường nên process
PATH chưa có CUDA. `CUDA UMD Version: 13.4` từ `nvidia-smi` chỉ phản ánh khả năng của driver,
không phải phiên bản Toolkit đang có trong PATH.

**Kiểm chứng:** tìm thấy `nvcc.exe`, `cudart64_110.dll`, `cublas64_11.dll` tại CUDA 11.6. Sau khi
thêm CUDA `bin` vào PATH của tiến trình kiểm tra, `nvcc` báo 11.6.124; focused test nạp
`JCublasBackend`, `Backend used: [CUDA]`, CUBLAS, nhận RTX 4050 Laptop GPU cc 8.9 và đạt 1/1.
Toàn bộ `mvn -B test` trên CUDA đạt 19 tests, 0 failures/errors/skips, `BUILD SUCCESS` trong
50,912 giây.

**Tệp ảnh hưởng:** `pom.xml`, `BackendConfigurationTest.java`, `README.md`,
`docs/current-state.md`, `docs/implementation-history.md`, `docs/change-log.md`. Không đổi model,
dữ liệu, normalization, split hoặc model selection.

**Giới hạn:** chưa chạy benchmark training CPU/GPU hoặc lượt 120 fits. Terminal/IDE cũ vẫn cần
đóng và mở lại; nếu không, phải thêm CUDA `bin` tạm vào `$env:Path` trước khi chạy Maven.

**Commit đề xuất:** `build: switch ND4J training backend to CUDA`

## 021 - 2026-10-10 - Hướng dẫn xem tiến trình training và đồng thời lưu log

**Mục tiêu:** giải thích vì sao terminal không hiển thị tiến trình khi chạy lệnh redirect và cung
cấp lệnh vừa xem output trực tiếp vừa giữ log đầy đủ.

**Phát hiện:** toán tử PowerShell `*> file` chuyển cả stdout/stderr khỏi terminal nên màn hình im
lặng. Chương trình hiện in mốc bắt đầu từng chuỗi, từng seed và kết quả seed sau khi Direct/AR hoàn
tất; nó chưa in phần trăm tổng hay từng epoch trong vòng fit. Dùng `Tee-Object` sẽ hiện các mốc sẵn
có trên terminal đồng thời ghi cùng nội dung vào file.

**Tệp ảnh hưởng:** chỉ `docs/change-log.md`; không thay đổi code hoặc chạy training.

**Kiểm tra:** đối chiếu các lệnh `System.out` trong multi-series/experiment runner và chạy
`git diff --check`. Không chạy Maven vì đây là hướng dẫn thao tác.

**Giới hạn:** giữa hai mốc seed, terminal vẫn có thể im lặng khá lâu vì forecaster chưa log từng
epoch. Muốn progress chính xác dạng `fit x/120` hoặc `epoch y/30` cần thay đổi code riêng; việc đọc
log liên tục không làm training nhanh hơn và không nên coi metric giữa chừng là kết quả cuối.

**Commit đề xuất:** `docs: explain live training progress logging`

## 022 - 2026-10-10 - Bổ sung tiến trình từng epoch cho multi-series training

**Mục tiêu:** cho biết chính xác lượt fit trong tổng 120 và epoch đã hoàn tất khi chạy Direct/AR
trên các chuỗi LOW/MEDIUM/HIGH, thay vì chờ đến cuối từng seed mới thấy kết quả.

**Lý do:** redirect stdout làm terminal im lặng; kể cả dùng `Tee-Object`, code cũ không phát sự kiện
trong vòng lặp epoch nên người dùng không thể phân biệt đang chạy bình thường hay bị treo.

**Thay đổi:** thêm `TrainingProgress` với công thức `series × seeds × 2 models`, định dạng gồm
fit/tổng fit, model, stratum, store-item, seed và epoch/tối đa. Truyền ngữ cảnh này từ
`MultiSeriesExperimentRunner` qua `ExperimentRunner` vào cả hai forecaster và in sau khi validation
epoch hoàn tất. Giữ nguyên các API fit/run cũ bằng overload không progress. Thêm hai JUnit test cho
mẫu định dạng yêu cầu và biên 1/120–120/120.

**Tệp ảnh hưởng:** `TrainingProgress.java`, `MultiSeriesExperimentRunner.java`,
`ExperimentRunner.java`, `SalesLstmForecaster.java`, `AutoregressiveLstmForecaster.java`,
`TrainingProgressTest.java`, `README.md`, `docs/current-state.md`,
`docs/implementation-history.md` và `docs/change-log.md`.

**Kết quả kiểm chứng:** bước red thất bại biên dịch đúng do chưa có `TrainingProgress`; focused test
sau triển khai đạt 2/2. Toàn bộ `mvn -B test` dùng `JCublasBackend`/RTX 4050 đạt 21 test, 0 lỗi,
`BUILD SUCCESS` trong 30,755 giây. Không chạy lại 120 fits và không đọc test 2017.

**Giới hạn:** con số `[x/120]` là vị trí fit, không phải phần trăm epoch toàn cục; early stopping có
thể làm mỗi fit ít hơn 30 epoch. Validation RMSE có trong history nội bộ của mỗi fit nhưng chưa được
xuất để vẽ; training loss chưa được thu thập. Lượt training đang chạy bằng JVM cũ không nhận thay
đổi, cần bắt đầu lượt mới. Không thay đổi kiến trúc, scaler train-only, chronological split, causal
features hoặc validation-only selection.

**Commit đề xuất:** `feat: report multi-series epoch progress`

## 023 - 2026-10-10 - Xuất learning curve train/validation theo epoch

**Mục tiêu:** thu thập train loss và validation loss của từng epoch để có thể vẽ learning curve,
quan sát hội tụ và dấu hiệu overfitting theo model, chuỗi và seed.

**Lý do:** code trước chỉ tính validation RMSE phục vụ early stopping; history không đi qua
`ExperimentRunner.SeedResult` và không có artifact dạng bảng để công cụ vẽ biểu đồ đọc trực tiếp.

**Thay đổi:** Direct và AR nay chấm MSE trên train/validation đã scale sau mỗi epoch, lưu cùng
validation RMSE sales trong `EpochMetrics`, giữ history qua kết quả từng seed và xuất
`output/training-history-validation.csv` sau khi multi-series hoàn tất. CSV có các cột `stratum`,
`store`, `item`, `model`, `seed`, `epoch`, `train_loss_scaled_mse`, `val_loss_scaled_mse`,
`val_rmse_sales`, `is_best_epoch`. Dòng progress cũng bổ sung ba metric. Thêm test cho getter loss,
tính hữu hạn qua synthetic training, schema/định dạng CSV và dấu thập phân `Locale.ROOT`.

**Tệp ảnh hưởng:** `Main.java`, `TrainingHistoryExporter.java`, `TrainingProgress.java`, hai
forecaster, `ExperimentRunner.java`, `TrainingHistoryTest.java`, `SalesLstmForecasterTest.java`,
`README.md`, `docs/current-state.md`, `docs/implementation-history.md`, `docs/change-log.md`.

**Kết quả kiểm chứng:** test đỏ ban đầu thất bại đúng vì chưa có getter loss. Một lỗi định dạng CRLF
Windows trong CSV được phát hiện và sửa thành newline ổn định. Focused tests đạt 5/5; toàn bộ
`mvn -B test` trên `JCublasBackend`/RTX 4050 đạt 23 test, 0 lỗi, `BUILD SUCCESS` trong 19,931 giây.
Không chạy lại 120 fits và không đọc test 2017.

**Giới hạn:** CSV được ghi cuối lượt nên không phục hồi history nếu tiến trình bị dừng giữa chừng;
lượt JVM đã chạy trước thay đổi không có dữ liệu mới. Hai forward pass chấm train/validation mỗi
epoch làm tăng runtime. Với AR, hai loss scaled là objective one-step/teacher-forced để so learning
curve; quyết định checkpoint vẫn dựa trên RMSE rollout 7 ngày ở đơn vị sales, không dùng test.

**Commit đề xuất:** `feat: export per-epoch training history`

## 024 - 2026-10-10 - Xác nhận lệnh chạy lại multi-series training

**Mục tiêu:** cung cấp lệnh PowerShell chạy đầy đủ 120 fits trên backend CUDA, đồng thời hiển thị
tiến trình trực tiếp và lưu stdout/stderr vào log ổn định.

**Phát hiện:** `exec-maven-plugin` vẫn cấu hình `com.dl4j.salesforecast.Main`; lệnh chạy hiện hành
là `mvn -B compile exec:java`. Dùng `2>&1 | Tee-Object` cho phép xem các dòng tiến trình epoch mới
trên terminal và lưu cùng nội dung tại `output/multi-series-run-2026-10-10.log`.

**Tệp ảnh hưởng:** chỉ `docs/change-log.md`; không thay đổi code, model, dữ liệu hoặc cấu hình.

**Kiểm tra:** đối chiếu `pom.xml`, README và các lệnh chạy multi-series đã ghi trong tài liệu.
Không chạy training hoặc Maven test vì yêu cầu chỉ hỏi lệnh vận hành. Terminal phải nhận CUDA 11.6
trong PATH; nếu là terminal cũ, cần thêm `v11.6\bin` tạm thời hoặc mở terminal/IDE mới.

**Giới hạn:** lệnh này khởi động lại toàn bộ 120 fits và ghi CSV history sau khi chương trình hoàn
tất; không tiếp tục được lượt JVM cũ từ checkpoint. Pipeline PowerShell hiển thị exit code của
pipeline, nên cần đọc dòng `BUILD SUCCESS`/`BUILD FAILURE` cuối log để xác nhận kết quả Maven.

**Commit đề xuất:** `docs: record GPU training command`

## 025 - 2026-10-10 - Làm rõ tổng số fit theo nhóm doanh số

**Mục tiêu:** phân biệt 120 lượt fit toàn thí nghiệm với số lượt của từng nhóm LOW/MEDIUM/HIGH và
số epoch bên trong mỗi fit.

**Phát hiện:** runner chọn tổng cộng 12 chuỗi, gồm 4 LOW, 4 MEDIUM và 4 HIGH. Mỗi chuỗi chạy 5 seed
cho 2 mô hình Direct/AR, vì vậy mỗi nhóm có `4 × 5 × 2 = 40` fits và cả ba nhóm có
`3 × 40 = 120` fits. Mỗi fit chạy tối đa 30 epoch nhưng có thể dừng sớm với patience 5; do đó tổng
trần là 3.600 epoch, không phải luôn luôn thực hiện đủ 3.600.

**Tệp ảnh hưởng:** chỉ `docs/change-log.md`; không thay đổi code hoặc chạy training.

**Kiểm tra:** đối chiếu `DEFAULT_SAMPLES_PER_STRATUM = 4`, năm default seed và hai forecaster trong
runner. Không chạy Maven vì đây là giải thích cấu hình hiện có.

**Giới hạn:** `[x/120]` biểu diễn vị trí fit, còn `epoch=y/30` biểu diễn tiến trình bên trong fit đó.

**Commit đề xuất:** `docs: clarify multi-series fit count`

## 026 - 2026-10-10 - Chẩn đoán lỗi class tổng hợp sau khi hoàn tất 120 fits

**Mục tiêu:** xác định nguyên nhân `NoClassDefFoundError: MultiSeriesExperimentRunner$1` ở cuối lượt
GPU training và phạm vi kết quả bị ảnh hưởng.

**Phát hiện:** log kết thúc lúc 14:46:55 có đủ vị trí fit 1–120, 2.693 dòng epoch và 60 dòng tổng
kết seed, nên phần train/validation của 12 chuỗi × 5 seed × 2 model đã hoàn tất. Lỗi xuất hiện sau
đó tại `MultiSeriesExperimentRunner.formatReport`, trước khi ghi các báo cáo mới. Class ngoài trong
`target/classes` vẫn tham chiếu class `$1` do Java compiler sinh cho enum switch, nhưng file
`MultiSeriesExperimentRunner$1.class` không tồn tại. Các class runner có timestamp 13:38:54, nằm
giữa lượt chạy 1 giờ 32 phút, cho thấy output biên dịch đã bị thay đổi bởi một lần compile/IDE
auto-build khác trong khi JVM Maven dài hạn vẫn đang chạy; class loader chỉ phát hiện thiếu class
khi nhánh tổng hợp được gọi cuối chương trình.

**Tệp ảnh hưởng:** chỉ cập nhật `docs/change-log.md`; không sửa code và không chạy lại training.

**Kiểm tra:** đối chiếu stack trace, liệt kê class runner trong `target/classes`, kiểm tra timestamp,
đếm progress/seed trong log và kiểm tra artifact output. `training-history-validation.csv` không
được tạo; `multi-series-validation.txt` có timestamp 2026-09-28 và
`error-analysis-validation.txt` có timestamp 13:28:23, nên cả hai không phải báo cáo hoàn chỉnh của
lượt vừa kết thúc.

**Giới hạn và phục hồi:** đối tượng kết quả trong bộ nhớ đã mất khi JVM thoát, nhưng log đầy đủ còn
giữ loss/RMSE từng epoch và metric tổng hợp từng seed, đủ để phục hồi CSV learning curve và phần lớn
kết quả mà không train lại. Baseline và breakdown chi tiết cần tái tính từ dữ liệu validation hoặc
runner phục hồi riêng. Không chạy `clean` hay ghi đè log trước khi phục hồi. Một build sạch trước
lượt sau sẽ sửa classpath, nhưng không tự tái tạo báo cáo của lượt đã kết thúc.

**Commit đề xuất:** `docs: diagnose post-training classpath failure`

## 027 - 2026-10-10 - Xác nhận nơi lưu kết quả của lượt training bị lỗi khi export

**Mục tiêu:** xác định dữ liệu của lượt 120 fits vừa hoàn tất còn được lưu ở đâu và artifact nào
chưa được tạo.

**Kết quả:** toàn bộ stdout/stderr của lượt chạy nằm trong
`output/multi-series-run-2026-10-10.log`. Log có các dòng progress chứa stratum, store-item, model,
seed, epoch, train loss, validation loss, validation RMSE và 60 dòng tổng kết seed. Vì exception xảy
ra tại lần gọi `MultiSeriesExperimentRunner.writeReport`, `output/training-history-validation.csv`
chưa được tạo và các TXT báo cáo hiện có mang timestamp cũ, không đại diện lượt vừa chạy. Kết quả
trong RAM không còn sau khi Maven JVM thoát.

**Tệp ảnh hưởng:** chỉ `docs/change-log.md`; không sửa code, không ghi đè log và không chạy lại train.

**Kiểm tra:** dùng bằng chứng đã đếm từ log ở chẩn đoán liền trước: 120 fit IDs, 2.693 dòng epoch và
60 dòng kết quả seed. `git diff --check` đạt.

**Giới hạn và tiếp theo:** log đủ để phục hồi CSV learning curve và metric từng seed mà không train
lại. Báo cáo baseline/macro/horizon đầy đủ cần parser phục hồi kết hợp tính lại phần validation từ
dataset; không được xóa hoặc ghi đè log trước khi phục hồi.

**Commit đề xuất:** `docs: record recoverable failed-run artifacts`

## 028 - 2026-10-10 - Phục hồi learning curve và metric seed từ log

**Mục tiêu:** tạo lại CSV learning curve và metric từng seed từ lượt GPU đã hoàn tất, không chạy lại
120 fits; sau đó xóa các file parser tạm nhưng giữ log nguồn và artifact phục hồi.

**Thực hiện:** tạo parser PowerShell tạm đọc định dạng progress/seed summary trong
`output/multi-series-run-2026-10-10.log`. Parser dùng `InvariantCulture`, ánh xạ best epoch theo dòng
tổng kết seed, kiểm tra RMSE tại checkpoint và xuất UTF-8 không BOM thành
`output/training-history-validation.csv` cùng `output/seed-metrics-validation.csv`. Sau khi kiểm tra
artifact, file `tools/recover-training-log.ps1` đã được xóa theo yêu cầu.

**Kết quả kiểm chứng:** phục hồi 2.693 dòng epoch, 60 dòng seed và 120 fit duy nhất; không có khóa
`stratum/store/item/model/seed/epoch` trùng, mỗi fit có đúng một `is_best_epoch=true`, mỗi nhóm
LOW/MEDIUM/HIGH có 20 dòng seed. CSV history có kích thước 158.286 byte và CSV seed 3.498 byte tại
thời điểm tạo. Đối chiếu dòng cuối store 10/item 28/seed 99 cho Direct 16,9311/21,2860 epoch 28 và
AR 16,8598/20,7802 epoch 14, khớp log. Không chạy training hoặc đọc test 2017.

**Tệp ảnh hưởng:** hai CSV trong `output` (artifact thường bị Git ignore), `docs/current-state.md`
và `docs/change-log.md`. Parser tạm không còn tồn tại; log nguồn không bị sửa hoặc xóa.

**Giới hạn:** số phục hồi có độ chính xác đúng bằng stdout: loss 8 chữ số thập phân, MAE/RMSE 4 chữ
số. CSV seed không chứa baseline hoặc metric theo horizon vì các giá trị này không được in trước khi
exception; muốn tái tạo chúng cần chấm lại validation/baseline nhưng không cần train model chỉ khi
còn prediction/checkpoint phù hợp. Hai CSV hiện chỉ phục vụ learning curve và metric từng seed.

**Commit đề xuất:** `docs: record recovered training artifacts`

## 029 - 2026-10-10 - Tạo báo cáo TXT dễ đọc từ metric phục hồi

**Mục tiêu:** cung cấp báo cáo tương tự `multi-series-validation.txt` từ lượt GPU mới mà không train
lại và không ghi đè artifact cũ.

**Thực hiện:** dùng parser PowerShell tạm đọc `output/seed-metrics-validation.csv`, tính trung bình
năm seed trong từng chuỗi, macro không trọng số theo nhóm và toàn bộ 12 chuỗi, sample standard
deviation giữa các series mean, số chuỗi Direct/AR có RMSE thấp hơn, rồi thêm bảng chi tiết 60 seed.
Kết quả được ghi UTF-8 vào `output/multi-series-validation-recovered.txt`. Parser tạm đã được xóa
sau khi kiểm tra.

**Kết quả kiểm chứng:** báo cáo có 101 dòng, 12 dòng series và đủ 60 dòng seed. Macro phục hồi:
Direct MAE/RMSE `10,3217/13,0076`, AR `9,7157/12,1396`; AR có mean RMSE thấp hơn ở 11/12 chuỗi.
Theo stratum, HIGH có RMSE lớn nhất: Direct `18,7081`, AR `17,0007`. Các số khớp phép tổng hợp từ
CSV seed; `git diff --check` và kiểm tra link Markdown đạt.

**Tệp ảnh hưởng:** `output/multi-series-validation-recovered.txt` (artifact thường bị Git ignore),
`docs/current-state.md` và `docs/change-log.md`. Không sửa code Java, không chạy training và không
đọc test 2017.

**Giới hạn:** báo cáo không có Weekly Naive, cải thiện so baseline hoặc breakdown horizon vì log
không chứa các giá trị đó. Metric dùng độ chính xác stdout bốn chữ số; macro có thể lệch rất nhỏ so
với tính trực tiếp từ giá trị double trong RAM. File `multi-series-validation.txt` cũ được giữ nguyên.

**Commit đề xuất:** `docs: add recovered validation report`

## 030 - 2026-10-10 - Xác định điều kiện tái tạo error analysis mới

**Mục tiêu:** làm rõ có cần train lại để tạo `output/error-analysis-validation.txt` từ lượt GPU vừa
hoàn tất hay không.

**Phát hiện:** log và hai CSV phục hồi đủ metric tổng hợp theo epoch, seed, series và stratum nên có
thể tạo báo cáo lỗi một phần mà không train lại. Weekly Naive cũng có thể tính lại từ validation gốc
mà không huấn luyện. Tuy nhiên log không lưu dự báo từng mẫu/horizon hoặc MAE/RMSE Direct/AR theo
horizon 1–7; model checkpoint cũng không được lưu. Vì vậy không thể tái tạo trung thực phần horizon
và mọi phép tổng hợp phụ thuộc prediction chi tiết từ artifact hiện có.

**Tệp ảnh hưởng:** chỉ `docs/change-log.md`; không sửa code, không chạy training và không thay đổi
artifact output.

**Kiểm tra:** đối chiếu schema `training-history-validation.csv`, `seed-metrics-validation.csv`,
progress/seed summary trong log và yêu cầu đầu vào của `ErrorAnalysisRunner`. `git diff --check` đạt.

**Giới hạn và lựa chọn:** muốn file error analysis đầy đủ như thiết kế hiện tại phải chạy lại model
hoặc có checkpoint/prediction chi tiết tương đương. Có thể tạo ngay bản partial ghi rõ thiếu horizon;
không được suy diễn horizon từ macro RMSE. Lần chạy sau nên xuất prediction/history theo từng fit
trước khi bước tổng hợp cuối để lỗi report không làm mất artifact.

**Commit đề xuất:** `docs: clarify error analysis recovery limits`

## 031 - 2026-10-10 - Đề xuất chuẩn hóa artifact cho mỗi lượt training

**Mục tiêu:** giảm tình trạng nhiều file cùng tên/timestamp khác nhau, tránh ghi đè báo cáo cũ và
đảm bảo vừa dễ lưu trữ, dễ truy vết, vừa có dữ liệu đủ để quan sát hoặc tái tạo phân tích mà không
train lại.

**Hiện trạng:** `Main` ghi bốn file tên cố định trực tiếp dưới `output`: thống kê dataset, báo cáo
multi-series, history epoch và error analysis; log lại được tạo ngoài chương trình bằng PowerShell.
Các artifact phục hồi bổ sung thêm seed CSV và report recovered, khiến file cũ/mới nằm chung thư mục
và khó xác định cùng một run. Quan trọng hơn, prediction theo mẫu/horizon và trạng thái run không
được ghi độc lập trước bước tổng hợp cuối.

**Đề xuất:** mỗi lần chạy tạo `output/runs/<run-id>/`, trong đó `run.json` ghi cấu hình, backend,
dataset fingerprint và trạng thái; `console.log` giữ stdout/stderr; `epoch-history.csv` ghi streaming
loss theo epoch; `seed-metrics.csv` ghi ngay sau mỗi seed; `validation-predictions.csv` giữ actual,
prediction, horizon và baseline theo từng mẫu; `summary.txt` là báo cáo đọc nhanh; `report.html` là
dashboard dẫn xuất chứa learning curve, lỗi theo stratum/horizon và so sánh baseline. Ghi CSV theo
kiểu append/flush hoặc file tạm rồi atomic rename, cập nhật manifest `RUNNING/SUCCESS/FAILED` để lỗi
tổng hợp cuối không làm mất dữ liệu đã chạy.

**Quản lý vòng đời:** coi ba CSV và `run.json` là nguồn dữ liệu chuẩn cần lưu; TXT/HTML có thể tái
tạo. `sales-statistics.txt` nên chuyển thành artifact theo dataset fingerprint, không sinh lặp cho
mỗi run. Không lưu 120 checkpoint mặc định; chỉ lưu checkpoint khi có nhu cầu inference hoặc debug,
và phải có retention policy. Có thể có `output/latest.txt` trỏ run gần nhất thay vì ghi đè file.

**Tệp ảnh hưởng:** chỉ `docs/change-log.md`; chưa thay đổi code hoặc cấu trúc output, chưa tạo/xóa
artifact và chưa chạy test/training.

**Kiểm tra:** kiểm kê file hiện có trong `output` và đối chiếu tất cả điểm ghi file ở `Main`,
`MultiSeriesExperimentRunner`, `TrainingHistoryExporter`, `ErrorAnalysisRunner` và
`SalesDataAnalyzer`. `git diff --check` đạt.

**Giới hạn:** đây là thiết kế đề xuất. Cần triển khai writer theo run-id, export prediction từng
horizon, manifest trạng thái, báo cáo HTML và migration/retention riêng; không tự động coi artifact
cũ là cùng provenance. Test 2017 vẫn không được dùng cho model selection.

**Commit đề xuất:** `docs: propose per-run artifact layout`

## 032 - 2026-10-10 - Triển khai artifact validation theo run-id

**Mục tiêu:** triển khai cấu trúc output đã thống nhất để lượt training thành công dễ quản lý, lưu
trữ, quan sát và đủ dữ liệu tái tạo error analysis mà không train lại.

**Thay đổi:** thêm `RunArtifactSession`, `SeedMetricsExporter`, `ValidationPredictionExporter` và
`ValidationPrediction`. Mỗi run tạo manifest `RUNNING/SUCCESS/FAILED`, dataset hash, backend, log
ứng dụng và thư mục riêng. Direct/AR giữ prediction validation chi tiết cùng Weekly Naive. Khi thành
công xuất `epoch-history.csv`, `seed-metrics.csv`, `validation-predictions.csv`, `summary.txt`,
`report.html` và `dataset-statistics.txt`; `output/runs/latest.txt` trỏ run mới nhất. Main không còn
ghi đè bốn artifact tên cố định ở root output.

**Kiểm tra:** test đỏ ban đầu thất bại đúng vì thiếu `RunArtifactSession`; focused tests đạt 4/4.
Toàn bộ `mvn -B test` trên JCublasBackend/RTX 4050 đạt 24 test, 0 lỗi, `BUILD SUCCESS` trong
39,835 giây. Test synthetic kiểm tra manifest RUNNING/FAILED, console log, prediction count,
horizon/date và baseline hữu hạn. `git diff --check` cùng liên kết Markdown đạt. Không chạy lại
120 fits và không đọc test 2017.

**Giới hạn:** chưa forward-test một run 120 fits thực tế. Các CSV/report thành công vẫn được ghi ở
cuối lượt thay vì streaming; nếu JVM chết giữa training chỉ manifest/log còn. `console.log` không
bao gồm Maven output trước khi `Main` khởi tạo session. `report.html` hiện trình bày summary và link
nguồn, chưa render chart tương tác; `epoch-history.csv` và `validation-predictions.csv` đã đủ nguồn
để bổ sung chart sau. Không lưu checkpoint để tránh chi phí lưu 120 model.

**Commit đề xuất:** `feat: organize validation artifacts by run`

## 033 - 2026-10-10 - Xác nhận lệnh chạy với cấu trúc artifact theo run-id

**Mục tiêu:** cung cấp lệnh PowerShell khởi động lượt training mới sau khi triển khai per-run output,
đồng thời tránh tái diễn classpath không đồng bộ trong `target/classes`.

**Kết quả:** dùng `mvn -B clean compile exec:java` để làm sạch và biên dịch một lần trước training;
pipe qua `Tee-Object` giữ log Maven bên ngoài. Ứng dụng tự tạo thư mục
`output/runs/<timestamp>_<backend>/` và `latest.txt`; không cần truyền output path thủ công.

**Tệp ảnh hưởng:** chỉ `docs/change-log.md`; không sửa code và không chạy training.

**Điều kiện vận hành:** không chạy Maven compile/test khác và không để IDE auto-build
`target/classes` trong khi 120 fits đang chạy. Xác nhận cuối lượt bằng cả `BUILD SUCCESS` và
`run.json` có trạng thái `SUCCESS`; `latest.txt` cho biết run-id mới nhất.

**Kiểm tra:** đối chiếu cấu hình `exec-maven-plugin`, `Main` và `RunArtifactSession` hiện tại;
`git diff --check` đạt.

**Giới hạn:** `clean` xóa build output cũ trước khi bắt đầu nhưng không xóa `output/runs` hoặc các
báo cáo đã lưu. Lệnh vẫn chạy lại toàn bộ 120 fits từ đầu và không resume checkpoint.

**Commit đề xuất:** `docs: record per-run training command`

## 034 - 2026-10-10 - Chẩn đoán lỗi đường dẫn khi khởi động training

**Mục tiêu:** giải thích lỗi `Could not find a part of the path ... emedic-dental\output\...` khi
chạy lệnh Maven training.

**Nguyên nhân:** terminal đang đứng tại repository `C:\Users\quangTam\Documents\GitHub\emedic-dental`
thay vì `C:\Users\quangTam\Documents\DL4j\sales-demand-forecasting-dl4j`. Vì vậy đường dẫn log
tương đối `output/...` được giải quyết dưới dự án Emedic, nơi không có thư mục output; đồng thời nếu
qua được bước ghi log thì Maven cũng không chạy đúng `pom.xml` của dự án DL4J.

**Khắc phục:** chuyển thư mục bằng `Set-Location` tới repository DL4J, bảo đảm `output` tồn tại rồi
mới tạo tên log và chạy `mvn -B clean compile exec:java | Tee-Object`.

**Tệp ảnh hưởng:** chỉ `docs/change-log.md`; không sửa code, không chạy Maven hoặc training.

**Kiểm tra:** đối chiếu prompt PowerShell do người dùng cung cấp với workspace root hiện tại;
`git diff --check` đạt.

**Giới hạn:** lệnh đúng vẫn chạy lại 120 fits từ đầu. Không chạy compile/test hoặc IDE auto-build
khác trong khi JVM training hoạt động.

**Commit đề xuất:** `docs: clarify training working directory`

## 035 - 2026-10-11 - Phân tích run GPU thành công và cập nhật trạng thái

**Mục tiêu:** xác minh artifact của run `2026-10-10_184201_989_gpu`, phân tích validation theo
model, stratum, horizon, seed và learning curve, rồi cập nhật current state và implementation history.

**Nguồn:** `run.json`, `summary.txt`, `seed-metrics.csv`, `epoch-history.csv` và
`validation-predictions.csv` trong thư mục run. Manifest ghi `SUCCESS`, `JCublasBackend`, Java
11.0.32.1, dataset SHA-256 cố định, validation 2016 và `testUsedForSelection=false`; thời lượng
1:34:52. Không đọc hoặc tính metric test 2017.

**Kết quả:** Weekly Naive macro MAE/RMSE 8,7365/11,1435; Direct 10,3217/13,0076; AR
9,7157/12,1396. AR giảm khoảng 6,67% RMSE so Direct nhưng vẫn kém baseline 8,94%. Direct/AR thắng
baseline lần lượt 3/12 và 4/12 chuỗi, đều thuộc LOW. LOW cải thiện RMSE +4,25%/+5,54%; MEDIUM kém
17,39%/10,10%; HIGH kém 27,43%/15,80%. HIGH có lỗi tuyệt đối lớn nhất; horizon 6 lớn nhất cho
Direct và horizon 7 lớn nhất cho AR.

**Learning curve và seed:** Direct best epoch trung bình 27,62, chạy 29,70, dừng sớm 12/60;
AR best 10,18, chạy 15,18 và dừng sớm 60/60. Macro RMSE theo seed có sample std 0,9213 Direct và
0,9244 AR. Chỉ AR seed 2026 đạt 11,1105, nhỉnh hơn baseline macro; các seed khác không xác nhận lợi
thế ổn định.

**Kiểm tra dữ liệu:** seed CSV có 60 dòng, history có 2.693 dòng, đủ 120 fit và 120 best flags.
Prediction CSV có 302.400 dòng, chia đều 151.200/model và 43.200/horizon; mỗi fit có 2.520 dự báo.
Tái tính MAE/RMSE từ prediction khớp seed metrics với sai lệch tối đa khoảng `5,1e-11`.
`git diff --check` và kiểm tra liên kết Markdown đạt. Không chạy training/test mới.

**Tệp ảnh hưởng:** `docs/current-state.md`, `docs/implementation-history.md` và
`docs/change-log.md`; không sửa code hoặc artifact run.

**Giới hạn:** 12/500 chuỗi, cửa sổ dự báo chồng lấn, validation dùng chọn epoch, không có confidence
interval hoặc kiểm định thống kê. Kết quả chưa phải đánh giá test cuối. Giả thuyết tiếp theo là LSTM
dự báo residual quanh Weekly Naive với biến đổi/loss ổn định phương sai fit train-only.

**Commit đề xuất:** `docs: record completed GPU validation run`

## 036 - 2026-10-11 - Tổng hợp báo cáo triển khai dự án, luồng xử lý và kết quả

**Mục tiêu:** lập báo cáo tổng hợp chi tiết về hiện trạng triển khai dự án, luồng xử lý (pipeline) dữ liệu/huấn luyện và các kết quả thực nghiệm đạt được từ run GPU chuẩn hóa gần nhất.

**Lý do:** người dùng yêu cầu báo cáo toàn diện về kiến trúc hệ thống, quy trình tiền xử lý, cấu hình mô hình và phân tích sâu kết quả validation để nắm bắt bức tranh toàn cảnh và định hướng các bước cải tiến kế tiếp.

**Nội dung tổng hợp:**
- Tổng quan bài toán: Dự báo chuỗi thời gian bán hàng 7 ngày cho các cặp cửa hàng - mặt hàng (500 cặp store-item, 2013–2017) trên nền tảng DeepLearning4J và backend CUDA 11.6 (NVIDIA RTX 4050).
- Luồng xử lý dữ liệu: Ingestion (`SalesDataAnalyzer`) -> Chronological split (Train 2013–2015, Validation 2016, Test 2017 holdout) -> Phân tầng tertile theo doanh số trung bình train (LOW, MEDIUM, HIGH) chọn 12 chuỗi đại diện -> Chuẩn hóa Min-Max train-only -> Cửa sổ 30 ngày lookback, horizon 7 ngày, 14 ngày warm-up, 6 causal features -> Input tensor `[batch, 6, 30]`.
- Kiến trúc & Mô hình: Baseline Weekly Naive, Direct Multi-Horizon LSTM và Autoregressive LSTM (rollout 1-step kèm tái tạo feature nhân quả). Chạy 5 seeds (42, 123, 2026, 7, 99), 120 fits tổng cộng, checkpoint chọn nghiêm ngặt theo validation RMSE. Quản lý artifact theo run-id (`output/runs/<run-id>/`).
- Kết quả đạt được (Run `2026-10-10_184201_989_gpu`): Weekly Naive macro MAE/RMSE `8,7365 / 11,1435`; AR LSTM `9,7157 / 12,1396`; Direct LSTM `10,3217 / 13,0076`. AR tốt hơn Direct 6,67% RMSE ở 11/12 chuỗi, nhưng Weekly Naive thắng tổng thể. Nhóm LOW là nhóm duy nhất hai LSTM thắng baseline (+4,25% Direct, +5,54% AR). MEDIUM và HIGH lỗi tăng mạnh (HIGH RMSE Direct 18,7081, AR 17,0007). Horizon breakdown cho thấy Weekly Naive ổn định ~11,14 trên cả 7 ngày, trong khi Direct lỗi nhất ở H6 và AR tăng dần khi rollout đến H7.
- Định hướng kế tiếp: Huấn luyện LSTM dự báo phần dư quanh Weekly Naive kết hợp biến đổi ổn định phương sai (`log1p` hoặc Huber loss) fit train-only.

**Tệp ảnh hưởng:** `docs/change-log.md`; không thay đổi code, cấu hình, dữ liệu hay kết quả mô hình.

**Kiểm tra:** đối chiếu số liệu từ `output/runs/2026-10-10_184201_989_gpu/summary.txt`, `run.json` và `docs/current-state.md`; chạy kiểm tra Markdown và `git diff --check`. Không chạy lại training hoặc Maven.

**Giới hạn:** số liệu dựa trên 12 chuỗi đại diện trong tập validation 2016, chưa đánh giá test 2017. Báo cáo phản ánh hiện trạng triển khai, chưa thực hiện code thay đổi mô hình mới.

**Commit đề xuất:** `docs: report project pipeline and empirical results`

## 037 - 2026-10-12 - Triển khai thí nghiệm Residual Autoregressive LSTM

**Mục tiêu:** học residual quanh Weekly Naive, giữ so sánh có kiểm soát với Weekly/Direct/AR;
hoàn tất code, JUnit, smoke và review trước khi cân nhắc full 12×5.

**Lý do:** run cũ cho thấy Weekly vẫn thắng macro; MEDIUM/HIGH là điểm yếu của LSTM tuyệt đối.
Thí nghiệm đầu chỉ đổi target formulation, không thêm Huber/log1p hoặc feature/kiến trúc mới.

**Thay đổi:** thêm `ResidualLstmForecaster`, `ExperimentConfiguration`, `ResidualComparisonReport`;
mở rộng AR dùng chung vòng fit/rollout với label residual chia sales scale train và reconstruction
cộng baseline. Giữ input, split, 12 đại diện, năm seed, MSE, Adam, cấu hình và validation-only
selection. Runner có `--residual` (180 fits) và `--residual-smoke` (1 chuỗi/1 seed/2 epoch mỗi
model), còn lệnh cũ giữ 120 fits. Artifact v2 thêm residual ở cuối CSV, ghi min/max/scale train,
config thật, báo cáo overall/stratum/horizon/seed/consistency/win rate; không ghi đè run cũ.

**Tệp ảnh hưởng:** Main, AR forecaster, ExperimentRunner, MultiSeriesExperimentRunner,
TrainingProgress, ba exporter CSV, RunArtifactSession; ba class mới nêu trên; hai test residual
mới và RunArtifactSessionTest; README, current-state, implementation-history, change-log và
[residual-experiment.md](residual-experiment.md) (có liên kết từng tệp cụ thể). Không sửa
SalesPreprocessor, Direct model, Weekly Naive hoặc dependency.

**Kiểm tra:** red test chạy và thất bại đúng tại assertion vì thiếu class residual (1 failure).
Focused model 4/4 đạt; suite tích hợp 31/31 đạt; suite cuối `mvn -B test` **33/33**, 0 failures,
errors, skips, exit 0, BUILD SUCCESS trong 1:12 phút. Test bảo vệ target +7/−10/0, reconstruction,
constant scale, train-only scaling, target containment, tensor/mask, finite H1–H7, future mutation,
test-independent selection, metric sales, macro/win và artifact lifecycle. Review không còn lỗi
chặn trong phạm vi kiểm tra. `git diff --check` và kiểm tra liên kết Markdown hoàn tất.

**Smoke:** `mvn -B compile exec:java '-Dexec.args=--residual-smoke'`, exit 0/BUILD SUCCESS,
38,900 giây; run `2026-10-12_002237_861_gpu` có SUCCESS, JCublasBackend, Java 11.0.32.1.
LOW store 7/item 34, seed 42, ba fit × 2 epoch; đủ 10 artifact, 6 epoch, 3 best flags, 1 seed row,
7.560 prediction (2.520/model), H1–H7 đầy đủ, không trùng khóa. Dataset hash khớp; reconstruction
trong `2e-10`; MAE/RMSE tính lại khớp seed CSV trong `2e-9`. Residual smoke RMSE 7,1030,
Weekly 6,8709; không dùng kết quả nhỏ này kết luận model tốt hơn.

**Giới hạn:** IMPLEMENTED / TESTED / SMOKE TESTED / FULL EXPERIMENT NOT YET RUN. Chưa có kết luận
MEDIUM/HIGH hoặc vượt baseline macro 11,1435. Full 180 fits ước khoảng 2:22 giờ dựa trên run
120 fits 1:34:52; README có lệnh lưu log. CSV đầy đủ xuất cuối lượt, chưa checkpoint/resume;
Java error ghi FAILED nhưng kill cưỡng bức/mất điện có thể để RUNNING. Không tính metric test 2017,
không sửa kết quả cũ, không commit/push.

**Commit đề xuất:** `feat: add residual LSTM forecasting experiment`

## 038 - 2026-10-12 - Xác nhận residual tiếp tục huấn luyện trên GPU

**Mục tiêu và lý do:** xác nhận thêm residual không chuyển backend huấn luyện sang CPU.

**Kết quả:** `pom.xml` vẫn dùng `nd4j-cuda-11.6-platform`; manifest smoke
`2026-10-12_002237_861_gpu` ghi SUCCESS và `org.nd4j.linalg.jcublas.JCublasBackend`.
Lệnh full residual dùng cùng backend, không cần thêm cờ GPU.

**Kiểm tra và giới hạn:** đối chiếu trực tiếp dependency và manifest; `git diff --check` đạt.
Không chạy training/test mới; full residual vẫn chưa chạy. Chỉ cập nhật nhật ký, không sửa code.

**Commit đề xuất:** `docs: confirm residual GPU backend`

## 039 - 2026-10-12 - Cập nhật tài liệu từ full Residual AR GPU

**Mục tiêu và lý do:** xác minh run full mới nhất, phân tích kết quả theo overall, stratum, horizon,
seed và learning curve, rồi cập nhật toàn bộ docs mà không thay thế ý nghĩa lịch sử của run cũ.

**Nguồn:** `run.json`, `summary.txt`, `error-analysis.txt`, `series-scaling.csv`, `seed-metrics.csv`,
`epoch-history.csv` và `validation-predictions.csv` của run `2026-10-12_092618_825_gpu`.
Manifest ghi SUCCESS, JCublasBackend, Java 11.0.32.1, 12 chuỗi, 5 seed, 3 neural model, 180 fits,
dataset hash không đổi, validation-only selection và test không tham gia selection. Run kéo dài
3:16:03, từ 09:26:18 đến 12:42:22 giờ Việt Nam.

**Kết quả:** Weekly/Direct/AR/ResidualAR có macro MAE lần lượt 8,7365/10,3217/9,7157/9,2169 và
RMSE 11,1435/13,0076/12,1396/11,6805. Residual giảm RMSE 10,20% so Direct và 3,78% so AR,
thắng mỗi model tại 8/12 chuỗi, nhưng kém Weekly 4,82% và thắng baseline 0/12. Residual thua AR
ở cả 4 LOW nhưng thắng AR ở cả 4 MEDIUM và 4 HIGH; khoảng cách RMSE với Weekly tại MEDIUM/HIGH
thu hẹp còn 5,20%/5,26%. Nó tốt hơn AR ở cả H1–H7 nhưng chưa thắng Weekly tại horizon nào.

**Seed và training:** macro RMSE Residual theo seed 42/123/2026/7/99 là
11,1603/12,6071/12,0819/11,2796/11,2735; sample std 0,6358, thấp hơn Direct 0,9213 và AR 0,9244.
Residual best epoch trung bình 9,55, chạy 14,55 và dừng sớm 60/60 fits.

**Kiểm tra:** CSV có 60 seed rows, 3.566 epoch rows, 180 best flags và 453.600 prediction,
chia đều 151.200/model và 64.800/horizon; tất cả khóa prediction duy nhất. Dataset hash khớp.
Tái tính MAE/RMSE khớp seed metrics với sai lệch tối đa `5,1e-11`; reconstruction residual khớp
trong khoảng `1,1e-10`. Kiểm tra liên kết Markdown và `git diff --check` đạt. Không chạy Maven
test hoặc training mới vì yêu cầu chỉ cập nhật báo cáo từ artifact đã hoàn tất.

**Tệp ảnh hưởng:** `README.md`, `docs/README.md`, `docs/current-state.md`,
`docs/implementation-history.md`, `docs/residual-experiment.md`, `docs/change-log.md`; không sửa
code Java, cấu hình, dependency hoặc artifact run.

**Giới hạn:** đánh giá trên 12/500 chuỗi, cửa sổ validation chồng lấn, validation vừa chọn epoch
vừa báo metric; chưa bootstrap/CI hoặc đánh giá test 2017. Residual cải thiện neural baselines nhưng
chưa thay thế Weekly Naive. Không commit/push.

**Commit đề xuất:** `docs: record full residual validation results`

## 040 - 2026-10-12 - Hoàn thiện ResidualAR Huber, tests và smoke GPU

**Mục tiêu và lý do:** kiểm chứng việc chỉ thay MSE bằng Huber, giữ residual formulation và
protocol; hoàn thiện implementation, TDD, review, smoke và tài liệu trước full experiment dài.

**Thay đổi:** thêm HuberLoss/LossMode; tái sử dụng fit/rollout của AR, overload MSE cũ giữ nguyên.
ND4J 1.0.0-M2.1 không có Huber chuẩn, dùng custom ILossFunction tương thích, delta 1 cố định.
12.552 train labels có |label| tối đa 0,6875; ghi rõ giới hạn Huber có thể gần quadratic.
Runner mới chỉ train ResidualAR và ResidualARHuber cùng run: full 120 fits, smoke 2 fits.
Schema 3 lưu lossFunction theo model/huberDelta, epoch loss không còn nhãn MSE sai;
báo cáo macro hai tầng, stratum, H1–H7, seed std/minmax, improvement so Weekly/MSE và strict wins.

**Tệp ảnh hưởng:** Main; AR/residual forecaster; ExperimentConfiguration/ExperimentRunner;
MultiSeriesExperimentRunner; ResidualComparisonReport; ba CSV exporter; RunArtifactSession;
HuberLoss/LossMode mới; ResidualHuberTest mới và hai test residual mở rộng; README/docs index,
current-state, implementation-history, change-log và residual-huber-experiment.md mới.
Giữ các cập nhật docs từ yêu cầu trước; không sửa preprocessing/Direct/Weekly/pom.xml.

**Kiểm tra:** RED 2 assertion failures/0 errors; focused 16/16; `mvn -B test` 39/39,
0 failures/errors/skips, exit 0/BUILD SUCCESS trong 1:31 phút. Test kiểm tra công thức/gradient,
mask, serialization, reconstruction, causal rollout, scaler/labels, validation RMSE checkpoint,
MSE compatibility, CLI/export/report và SUCCESS/FAILED lifecycle. Review không thấy lỗi chặn.

**Smoke:** `2026-10-12_143207_100_gpu`, SUCCESS, JCublasBackend, Java 11.0.32.1;
Maven exit 0/BUILD SUCCESS 38,555 giây. LOW 7/34, seed 42, 2 epoch/model, hai best epoch 2.
Đủ 10 artifact, 1 seed row, 4 epoch rows, 2 best flags, 5.040 predictions và H1–H7/model.
Hash đúng, keys duy nhất, predictions finite; recompute metric lệch tối đa `2,642e-11`,
reconstruction `3,553e-15`. MSE predictions khớp hoàn toàn smoke lịch sử cùng protocol.
Weekly/MSE/Huber RMSE 6,870861/7,102983/7,103008; không kết luận chất lượng từ smoke.

**Trạng thái và giới hạn:** IMPLEMENTED / TESTED / SMOKE TESTED / FULL EXPERIMENT NOT YET RUN.
Full 120 fits ước thô 2:10:42, lệnh lưu log trong [hồ sơ Huber](residual-huber-experiment.md).
Chưa đánh giá MEDIUM/HIGH, seed stability hoặc thắng Weekly trên 12×5. Không sử dụng test 2017
để chọn model/delta; không triển khai log1p, không commit/push. Kiểm tra local Markdown links và
`git diff --check` đạt; logs nằm dưới output/huber-*.log.

**Commit đề xuất:** `feat: add Huber loss experiment for residual LSTM`


## 041 - 2026-10-12 - Báo cáo full ResidualAR Huber trên GPU

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

## 042 - 2026-10-12 - Global ResidualAR một cửa hàng, nhiều items

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

## 043 - 2026-10-13 - Profile và tối ưu runtime global, benchmark/equivalence

**Yêu cầu và lý do:** profile trước tối ưu, giảm runtime giữ nguyên model/protocol/số học;
benchmark50items trước/sau, tests, audit và chuẩn bị full command, không chạy full5seeds.

**Thay đổi:** benchmarkCLI giới hạn riêngseed42/2epochs, giữ toàn bộ50items/batch64;
nanoTime profile tách data/train/score/validation/output/features/export/manifest; SHA256 exact
ordered windows. Giữ bestValidation immutable tại checkpoint để xuất cuối fit, không inference
lại cùng weights. Script compare audit chặt1e-9; Invoke-GlobalRun tạo build snapshot riêng
và Tee log/exitcode, tránh IDE ghi lại classes. Mọi epoch vẫn validation đủ18.000×7.

**Kết quả xác minh:** REDbenchmarkCLI1failure/0errors, REDcache1failure/0errors đúng nguyên nhân;
focused12/12 PASS, `mvn -B -f output/benchmark-build-after/pom.xml test`51/51 PASS,
0failures/errors/skips, exit0/BUILD SUCCESS2:12. BEFORE `2026-10-12_201423_002_gpu`
và AFTER `2026-10-13_002648_831_gpu` SUCCESS/exit0/JCublasBackend cùng dataset/config.
Ứng dụng1.202,922→1.060,531s, giảm142,391s/11,84%, speedup1,1343×;
validation543,474→270,364s gồm bỏ final evaluation187,425s, outputcalls5.922→3.948.
126.000predictions xuất đủ và khớp tại precisionCSV, max/meanpredictiondiff0;
scaler/Weekly/keys/windowhashes và peritem/epoch/macro/pooledmetrics diff0, bestepoch2.
Audit output/global-performance-comparison.json PASS; recompute4,936e-11, reconstructionCSV1,001e-10.

**Giới hạn và review:** một cặp benchmark, train652,193→782,259s do biến động runtime chưa
cô lập nguồn; không claim2× kernels. Cache chỉ giữ best snapshotCPU, không thay population/
checkpointfrequency/RNG/train-onlyscaler/no teacherforcing/testselection. Review không thấy
lỗi chặn trong scope kiểm tra. AFTERmean8,772phút/epoch; full150epochước21,93giờ,
tốc độhai epoch cho15,1–28,75giờ, chưa biết earlystop. FULL GLOBAL EXPERIMENT NOT RUN.
Runđầu195613FAILED/classmismatch không dùng làmbaseline. Automation10phút PAUSED theo yêu cầu.

**Tệp và evidence:** xem [hồ sơ global](single-store-global-experiment.md#runtime-optimization-ngày-2026-10-13)
cho danh sách source/tests/scripts, profile/logs hai run, bảngphase, hashes, memory, command và
giới hạn. Cập nhật README/docs index/current-state/history/change-log/global; giữ lịch sử cũ.
Không commit/push.

**Commit đề xuất:** `perf: optimize global forecasting runtime without changing protocol`

**Kiểm tra bàn giao:** smoke qua script `2026-10-13_005123_869_gpu` SUCCESS, Maven exit 0,
BUILD SUCCESS 59,509s. SHA256 file predictions khớp smoke lịch sử cùng config.
47 liên kết Markdown local hợp lệ; `git diff --check` đạt. Lịch kiểm tra mỗi 10 phút đã PAUSED.

## 044 - 2026-10-13 - Chuẩn bị global 3 seed, checkpoint và deployment package

**Mục tiêu:** hoàn thiện source để copy sang máy GPU mạnh, chạy một command cho Store 1/50 items
với seeds 42, 123, 2026, lưu best checkpoint từng seed và tự chọn model deployment; không chạy
full training, benchmark dài hoặc smoke dataset thật trong lượt này.

**Lý do:** global runner trước dùng chung danh sách 5 seed nghiên cứu, best params chỉ ở RAM và
chưa persist model/scaler/mapping/schema đủ cho inference hoặc audit sau training.

**Đã thay đổi:** `ExperimentConfiguration` quản lý seed global/CLI; checkpoint DL4J ghi temp,
restore, so params/prediction rồi atomic move; metadata chỉ usable sau verify. Mỗi seed giữ model;
selector dùng validation Macro RMSE, tie-break Macro MAE rồi seed nhỏ. Deployment copy checkpoint
thắng và persist scaler, deterministic mapping, feature schema, selection và dataset hash.
Manifest dùng dataset path tương đối. Thêm preflight không fit network, script training một lệnh,
script audit và hướng dẫn GPU. Giữ nguyên protocol/model/hyperparameter/split/scaler train-only;
legacy research vẫn 5 seed. Không đổi `pom.xml`, dependency, commit hoặc push.

**Tệp chính:** `ExperimentConfiguration`, `Main`, `GlobalExperimentRunner`,
`GlobalResidualLstmForecaster`, `SingleStoreGlobalDataset`, `GlobalExperimentReport`,
`RunArtifactSession`, `GlobalModelArtifacts`, `GlobalPreflight`; global tests; ba PowerShell scripts;
README và năm tài liệu trong `docs/`.

**Kiểm tra:** RED ban đầu xác nhận global còn 5 fits; vòng đầu còn bắt lỗi Java API close và fixture
horizon. Focused selector/corrupt checkpoint 2/2 PASS; regression legacy 10/10 PASS. Cuối cùng
`mvn -B -Dmaven.repo.local=C:\Users\quangTam\.m2\repository test`: **53/53 PASS**, 0 failures,
0 errors, 0 skipped, BUILD SUCCESS, 2:46. Test synthetic xác nhận checkpoint best không phải epoch
cuối, restore model tái tạo validation Macro RMSE, corrupt/missing checkpoint bị từ chối và package
trỏ đúng model. Không chạy full/smoke/benchmark dataset thật.

**Giới hạn:** full global 3 seed và preflight trên máy GPU đích chưa chạy; chưa có model deployment
thật hoặc runtime mới; audit 150/378.000 chỉ có thể chạy sau full. Chưa hỗ trợ aggregate các run
độc lập từ nhiều máy.

**Commit đề xuất:** `feat: prepare portable global DL4J training and model selection`

## 045 - 2026-10-13 - Thêm calibration batch cho workflow RTX 3090

**Mục tiêu:** xác nhận và triển khai protocol mới preflight → GPU smoke → calibration ba cặp
train/validation batch → full ba seed tối đa 10 epoch → checkpoint/deployment.

**Lý do:** RTX 3090 24GB có thể dùng batch lớn hơn; cần đo sec/epoch và validation Macro RMSE
trước khi khóa cấu hình full, thay vì tiếp tục batch 64/max 30 không hiệu chỉnh trên máy đích.

**Đã thay đổi:** `ExperimentConfiguration` tách train/validation batch, thêm calibration một epoch
seed 42 và giảm full global xuống max 10 epoch. Forecaster dùng validation batch riêng. Thêm selector
Java và PowerShell với policy baseline 64/256, ngưỡng RMSE +1%, ưu tiên thời gian, RMSE, batch nhỏ.
`Invoke-GlobalTraining.ps1` chạy tuần tự preflight, smoke 64/256, calibration 64/256–128/512–
256/1024 và full bằng candidate thắng. Manifest/model metadata/deployment selection ghi cả hai batch;
run full nhận bản sao `calibration-selection.json`. Calibration không tạo deployment giả.

**Tệp chính:** configuration, forecaster, runner/report/Main/session/model artifacts; test calibration;
`Invoke-GlobalRun.ps1`, `Invoke-GlobalTraining.ps1`, `Select-GlobalCalibration.ps1`, audit script;
README và tài liệu global/GPU/current-state/history/change-log.

**Kiểm tra:** RED test compile xác nhận API calibration chưa tồn tại. Focused sau triển khai 14/14
PASS. `mvn -B -Dmaven.repo.local=C:\Users\quangTam\.m2\repository test`: **55/55 PASS**, không
failure/error/skip, BUILD SUCCESS, 3:31. PowerShell parser PASS; integration fixture chọn đúng
128/512 và loại 256/1024 dù nhanh hơn vì RMSE vượt ngưỡng. Không chạy dữ liệu thật.

**Giới hạn:** chưa chạy preflight/smoke/calibration/full trên RTX 3090; chưa biết batch thắng,
sec/epoch, RMSE, runtime full hoặc deployment model. Ngưỡng 1% là policy vận hành đã ghi rõ,
không phải kết luận thống kê từ nhiều seed.

**Commit đề xuất:** `feat: add calibrated RTX 3090 global training workflow`

## 046 - 2026-10-13 - Xác nhận dữ liệu cần mang sang máy training

**Mục tiêu:** làm rõ sau khi clone source trên máy RTX 3090 có phải chỉ cần bổ sung database hay không.

**Kết luận:** project không dùng SQL/database server. Input thực tế là file CSV cục bộ
`data/train.csv`; thư mục `data/**` bị Git ignore nên clone repository không mang theo dataset.
Ngoài source đã clone, cần copy đúng file này và chuẩn bị JDK 11, Maven, NVIDIA driver/CUDA tương
thích backend `nd4j-cuda-11.6-platform`. Maven tải dependency theo `pom.xml` nếu cache máy mới chưa có.
`output/`, model, checkpoint và log là artifact sinh ra, không cần copy để bắt đầu training mới.

**Bằng chứng:** `.gitignore` chỉ cho phép `data/.gitkeep`; `git ls-files` xác nhận dataset thật
không được track. `Main`, preflight và hai script training đều đọc cố định `data/train.csv`.
`pom.xml` khai báo CUDA backend; README/GPU guide yêu cầu Java 11 và Maven. Không tìm thấy kết nối
database trong đường chạy global.

**Kiểm tra:** chỉ đọc source/cấu hình và `git status`; không sửa code, không chạy test hoặc training.
Markdown links và `git diff --check` được kiểm lại sau cập nhật tài liệu.

**Giới hạn:** chưa kiểm tra trực tiếp Java, Maven, CUDA, driver, dung lượng đĩa hoặc dataset trên
máy khách; preflight trên máy đó vẫn là bằng chứng bắt buộc trước smoke/calibration/full.

**Commit đề xuất:** `docs: clarify GPU machine data requirements`

## 047 - 2026-10-13 - Thêm evaluation-only Test 2017 cho GlobalResidualAR

**Mục tiêu và lý do:** dùng ba best-validation checkpoint seed 42/123/2026 của Full run
`2026-10-13_025422_624_gpu` để đánh giá Test 2017 ở một bước riêng, bảo đảm test không tham gia
training, chọn epoch/seed/hyperparameter hoặc thay deployment.

**Đã thay đổi:** `SingleStoreGlobalDataset` expose test; `SalesPreprocessor` nhận scaler artifact mà
không refit. Thêm `GlobalTestEvaluationRunner`, entry point và script PowerShell riêng. Runner fail-fast
theo dataset hash, 50 scaler, source metadata, epoch 11/20/13, seed list và deployment seed 123; restore
checkpoint trực tiếp, kiểm tra weights và SHA-256 trước/sau, dùng rollout causal và cùng keys với Weekly.
Output mới gồm bảy artifact trong `test-evaluation/`, manifest runtime/status và báo cáo macro/pooled,
per-seed, 50 items, H1-H7, LOW/MEDIUM/HIGH. Không sửa Full runner, architecture, hyperparameter,
checkpoint, deployment package hoặc validation artifact.

**Tệp:** thêm `GlobalTestEvaluationMain`, `GlobalTestEvaluationRunner`, focused test và
`Invoke-GlobalTestEvaluation.ps1`; sửa dataset/preprocessor, `GlobalStoreTest`, README và tài liệu dự án.

**Kiểm tra:** TDD RED compile đúng do thiếu scaler artifact/test samples. Focused evaluation 4/4 PASS;
full `mvn -B test` 59/59 PASS, 0 failures/errors/skips, BUILD SUCCESS 3:16 trên JCublasBackend.
PowerShell parser PASS. Full-panel regression xác nhận 359 test origins/item, 17.950 origins tổng;
synthetic inference xác nhận weights và checkpoint hash không đổi, H1-H7/key coverage khớp Weekly.
`git diff --check` và liên kết Markdown được kiểm tra sau cập nhật.

**Giới hạn:** chưa chạy Test 2017 trên checkpoint thật và chưa tạo `test-evaluation/`, đúng yêu cầu dừng
chờ xác nhận command. Do đó chưa có metric, runtime hay kiểm chứng 376.950 rows thực tế. Không commit/push.

**Commit đề xuất:** `feat: add checkpoint-only global test evaluation`

## 048 - 2026-10-13 - Sửa launcher evaluation-only gọi sai Main

**Mục tiêu và nguyên nhân:** sửa script Test 2017 đang in evaluation-only nhưng Maven thực tế gọi
`com.dl4j.salesforecast.Main`. Inspection và probe `--invalid` xác nhận `pom.xml` hard-code Main;
stack trace đi vào `ExperimentConfiguration.fromArgs` và fail trước evaluation.

**Thay đổi:** parameterize `<mainClass>${exec.mainClass}</mainClass>` và đặt default property vẫn là
`com.dl4j.salesforecast.Main`. Script dùng PowerShell argument array, đặt explicit
`GlobalTestEvaluationMain` và `--source-run` trước goals `compile exec:java`. Bỏ lời gọi
`ExperimentConfiguration.fromArgs` thừa khỏi evaluation runner. Không thêm mode vào Main và không sửa
model, hyperparameter, split, scaler, checkpoint hay deployment.

**Kiểm tra:** TDD RED 1/1 failure đúng vì POM chưa parameterize. Sau sửa, test launcher 1/1 PASS,
focused evaluation 5/5 PASS và full Maven 60/60 PASS, 0 failure/error/skip, BUILD SUCCESS 2:06.
Probe Maven với `-Dexec.args=--invalid` đi đúng vào `GlobalTestEvaluationMain.java:10`, không đọc dataset
hoặc checkpoint. PowerShell parser và `git diff --check` PASS.

**An toàn và giới hạn:** không chạy training hay Test 2017; `test-evaluation/` vẫn không tồn tại.
SHA-256 ba checkpoint và deployment model giữ nguyên so với trước sửa. Chưa chạy command thật; người
dùng phải xác nhận riêng trước evaluation. Không commit/push.

**Commit đề xuất:** `fix: route test evaluation to its dedicated main class`

## 049 - 2026-10-13 - Sửa lỗi HIGH bị parse thành horizon

**Mục tiêu và nguyên nhân:** khắc phục lần evaluation-only đã load đúng ba checkpoint và hoàn tất inference
nhưng fail khi tạo summary. `Group` dùng `startsWith("H")`; tier `HIGH` vì vậy bị cắt thành `IGH` rồi
đưa vào `Integer.parseInt`, gây `NumberFormatException`.

**Thay đổi:** điều kiện horizon chỉ chấp nhận regex `H[1-7]`. Thêm test cho `HIGH` không phát sinh lỗi,
ID horizon bằng 0, đồng thời `H7` vẫn có ID 7. Không sửa công thức metric, aggregation, inference,
scaler, split, model, checkpoint hoặc deployment.

**Kiểm tra:** TDD RED 1/1 failure tái hiện đúng `For input string: "IGH"`. Sau sửa, focused evaluation
6/6 PASS; full Maven 61/61 PASS, 0 failure/error/skip, BUILD SUCCESS 1:59. `git diff --check` và parser
PowerShell được kiểm tra sau cập nhật. Không chạy lại Test 2017 và không training.

**Artifact:** lần FAILED trước có manifest xác nhận `trainingPerformed=false`, `weightsUpdated=false`
và SHA-256 checkpoint trước/sau giống nhau. Thư mục partial chỉ chứa manifest, được di chuyển bảo toàn sang
`test-evaluation-failed-2026-10-13_075006`; không xóa dữ liệu và `test-evaluation/` hiện chưa tồn tại.

**Giới hạn:** chưa có evaluation SUCCESS hoặc metrics/report cuối; cần người dùng chạy lại command riêng.
Không commit/push.

**Commit đề xuất:** `fix: distinguish HIGH tier from horizon labels`

## 050 - 2026-10-13 - Audit repository và triển khai Docker foundation

**Mục tiêu và lý do:** bắt đầu hệ thống Big Data Sales Demand Forecasting bằng Giai đoạn 0-1 trên
repository thật, xác minh baseline/artifact trước khi sửa và tạo hạ tầng Docker Compose phù hợp Windows 11.

**Đã thay đổi:** tạo `docs/00-repository-audit.md`, `docs/01-architecture.md`, `compose.yaml`,
`.env.example`, hướng dẫn nhanh trong `README.md`, MinIO init script, Java 11 bootstrap health service, frontend bootstrap và thư mục
`spark-etl`. Cập nhật mục lục, lịch sử triển khai và trạng thái hiện tại. Compose định nghĩa MinIO,
PostgreSQL, Spark master + 2 workers + job runner, Forecast, Backend và Frontend; có network, volumes,
healthchecks, restart policy, port qua env và resource limits.

**Bằng chứng audit:** Java 11.0.32.1, Maven 3.9.16, DL4J/ND4J 1.0.0-M2.1; `data/train.csv` và run
`2026-10-13_025422_624_gpu` tồn tại. Deployment package có seed 123 model, 50 scaler và schema 56 kênh.
Test manifest `SUCCESS` ghi 17.950 origins, 376.950 points, không training/đổi weights và checkpoint
seed 123 SHA-256 `f9d2dee2d3fcfb31ed61e08cd7b9b5f92d8ca9a7f1b0a95e5447afa477c7a482`.

**Kiểm tra:** `docker compose --env-file .env.example config --quiet` PASS; `javac` Java 11 cho
bootstrap service PASS; `git diff --check` và liên kết Markdown được kiểm tra sau cập nhật. Không chạy
training, không sửa model/scaler/checkpoint và không commit/push.

**Giới hạn:** Docker Desktop Linux engine chưa chạy dù CLI 29.8.2/Compose 5.5.1 có mặt, nên chưa thể
pull/build/start và chưa xác minh health thực, Spark đăng ký hai worker, MinIO bucket, persistence hoặc
CPU checkpoint restore. Ba application service mới chỉ là bootstrap; Giai đoạn 2-9 chưa hoàn thành.

**Commit đề xuất:** `feat: add audited big data docker foundation`

## 051 - 2026-10-13 - Chạy thật Docker stack và tiếp tục Data Lake

**Mục tiêu và lý do:** người dùng đã bật Docker Desktop, yêu cầu kiểm tra lại Giai đoạn 1 và tiếp tục
triển khai. Cần sửa image pull lỗi, chứng minh cluster thật và đưa dữ liệu/artifact vào MinIO.

**Thay đổi:** đổi sang Bitnami legacy MinIO/Client có manifest pull được và Spark 3.5.9 Java 11;
mount dataset/deployment read-only cho init job; ingest Bronze cùng metadata và mirror sáu artifact seed
123. Thêm `docs/02-data-pipeline.md` và module `spark-etl` với quality gate fail-fast cho các count Store 1.

**TDD:** focused test ban đầu RED 1/1 vì `UnsupportedOperationException: not implemented`. Sau khi triển
khai rule, focused test GREEN 1/1, 0 failure/error. Rule từ chối sai row/split hoặc duplicate.

**Kết quả live:** Compose build/start exit 0; 10 service được tạo. MinIO/PostgreSQL/Forecast/Backend/
Frontend healthy; frontend HTTP 200. Spark Master 3.5.9 đăng ký đúng hai worker ALIVE, mỗi worker
1 core/1.024 MiB. MinIO init exit 0, tạo năm bucket; Bronze có CSV 16 MiB và metadata, artifact prefix
có sáu file. Sau restart, MinIO vẫn giữ CSV và PostgreSQL vẫn trả probe count 1.

**Giới hạn:** image pull đầu tiên rất lâu nhưng đã cache. Forecast/API/Frontend vẫn là bootstrap.
Spark dependency download cho runtime job được dừng sau khi RED test bị kéo theo dependency không cần
thiết; POM tách chúng vào profile `spark-runtime`. Chưa build/chạy profile đó, chưa có Silver/Gold,
feature parity, window/origin count hoặc benchmark; Giai đoạn 3 chưa hoàn thành.

**Kiểm tra cuối:** `mvn -B -f spark-etl/pom.xml test` PASS 1/1; `docker compose config --quiet`,
`git diff --check`, liên kết Markdown và các HTTP health check đều PASS. Full root `mvn -B test`
được khởi động nhưng phải dừng trong lúc Maven còn tải gói CUDA Windows lớn, nên lượt này không được
ghi nhận là full-suite PASS hay FAIL.

**Commit đề xuất:** `feat: verify docker stack and bootstrap data lake`

## 052 - 2026-10-13 - Kiểm tra trạng thái tải Docker và CUDA Maven

**Mục tiêu và lý do:** làm rõ thành phần nào đã tải xong sau lần triển khai Docker và liệu người dùng
có phải tải phụ thuộc thủ công hay không.

**Kết quả kiểm tra:** các image Spark 3.5.9 Java 11, MinIO, MinIO Client và PostgreSQL đều đã có trong
Docker image store; các service dài hạn của Compose vẫn đang chạy, trong đó các service có healthcheck
đều healthy. Maven cache đã có CUDA classifier Windows thường nhưng chưa có
`cuda-11.6-8.3-1.5.7-windows-x86_64-redist.jar`; chỉ còn file tải tạm 0 byte từ lần test bị dừng.

**Kết luận:** không cần tải thủ công. Maven Central sẽ tự tải artifact redist còn thiếu khi chạy lại
`mvn -B test`; cần giữ mạng ổn định và không dừng tiến trình trong lúc tải. Không sửa code, cấu hình,
model, scaler hay checkpoint và không chạy training.

**Giới hạn:** lượt kiểm tra này chỉ xác nhận trạng thái cache/image hiện tại; chưa chạy lại full test để
tải hoàn tất artifact lớn và chưa có kết quả full-suite mới.

**Commit đề xuất:** `docs: record docker and cuda download status`

## 053 - 2026-10-13 - Xác nhận lệnh chạy full test tại terminal

**Mục tiêu:** xác nhận cách tiếp tục tải phụ thuộc và chạy bộ test gốc.

**Hướng dẫn:** tại terminal đang đứng ở thư mục gốc repository
`C:\Users\quangTam\Documents\DL4j\sales-demand-forecasting-dl4j`, chạy `mvn -B test` và giữ cửa sổ,
kết nối mạng hoạt động cho đến khi Maven kết thúc. Maven sẽ tự tải artifact CUDA còn thiếu; không cần
tải hoặc chép JAR thủ công. Docker có thể tiếp tục chạy nhưng không phải điều kiện của bộ unit test gốc.

**Thay đổi và kiểm tra:** không sửa code/cấu hình và không chạy test trong lượt trả lời hướng dẫn này.
Liên kết Markdown và `git diff --check` được kiểm tra. Kết quả chỉ được xem là PASS khi terminal in
`BUILD SUCCESS`; nếu dừng lúc tải dependency thì chưa phải kết quả test.

**Commit đề xuất:** `docs: clarify full test command`

## 054 - 2026-10-13 - Cập nhật checklist tiến độ triển khai

**Mục tiêu và lý do:** đối chiếu toàn bộ yêu cầu Big Data ban đầu trong lúc người dùng chờ Maven tải
phụ thuộc, cung cấp trạng thái thực tế thay vì chỉ liệt kê kế hoạch.

**Thay đổi:** thêm `docs/11-implementation-checklist.md`, liên kết từ mục lục và cập nhật lịch sử/trạng
thái hiện tại. Checklist phân biệt đã đạt, đạt một phần và chưa đạt cho từng phase, thành phần đích và
12 acceptance criteria; đồng thời ghi đường găng triển khai tiếp theo.

**Kết quả:** 3/12 tiêu chí đã có bằng chứng đạt, 3/12 đạt một phần và 6/12 chưa đạt. Docker/Spark
topology và persistence đã được chứng minh; Spark Silver/Gold, DL4J serving, API, dashboard, parity/E2E
và benchmark chưa hoàn thành. Không sửa code, model, scaler, checkpoint hoặc artifact thực nghiệm.

**Kiểm tra:** đối chiếu file thực tế và `docker compose ps`; kiểm tra liên kết Markdown và
`git diff --check`. Không chạy thêm Maven test để tránh cạnh tranh với lượt tải/test của người dùng.

**Commit đề xuất:** `docs: add big data implementation checklist`

## 055 - 2026-10-13 - Làm rõ lỗi nhập đường dẫn trong PowerShell

**Mục tiêu:** hướng dẫn chạy full test sau khi PowerShell báo đường dẫn repository không phải cmdlet.

**Chẩn đoán:** prompt đã hiển thị terminal đang đứng tại repository và branch
`feature/bigdata-docker-platform`. Người dùng nhập lại đường dẫn trần nên PowerShell cố thực thi đường
dẫn đó như tên chương trình. Không có lỗi repository, Maven hay Docker được chứng minh bởi thông báo này.

**Hướng dẫn:** chạy trực tiếp `mvn -B test`. Nếu terminal ở thư mục khác thì dùng
`Set-Location 'C:\Users\quangTam\Documents\DL4j\sales-demand-forecasting-dl4j'` trước. Không sửa code
hoặc cấu hình và không chạy test thay người dùng trong lượt hướng dẫn này.

**Kiểm tra:** liên kết Markdown và `git diff --check` PASS. Kết quả test vẫn phải chờ terminal trả
`BUILD SUCCESS` hoặc `BUILD FAILURE`.

**Commit đề xuất:** `docs: clarify powershell test invocation`

## 056 - 2026-10-13 - Ghi nhận full test thành công và phần triển khai còn lại

**Mục tiêu:** cập nhật tiến độ sau khi người dùng hoàn tất `mvn -B test` và xác định các hạng mục còn
thiếu để hoàn thành yêu cầu Big Data end-to-end.

**Kết quả do người dùng cung cấp:** 63 test PASS, 0 failure, 0 error, 0 skip; `BUILD SUCCESS`; thời gian
04:46; kết thúc lúc 18:09:14+07:00 ngày 2026-10-13. Đây là bằng chứng terminal do người dùng gửi, không
phải lượt Maven do trợ lý chạy lại.

**Thay đổi tài liệu:** cập nhật checklist, trạng thái hiện tại và lịch sử triển khai. Tiêu chí bảo toàn
training/evaluation cũ chuyển sang đạt. Sửa tổng đếm từ bản checklist trước theo trạng thái thực tế trong
bảng thành 4/12 đạt, 1/12 đạt một phần và 7/12 chưa đạt.

**Phần còn lại:** Spark Silver/Gold runtime và parity; Forecast Service DL4J CPU; PostgreSQL migrations
và Spring Boot API; React dashboard; integration/E2E; benchmark 1/2 Workers; tài liệu 03-10 và demo.
Không sửa code/model/scaler/checkpoint và không chạy training trong yêu cầu cập nhật trạng thái này.

**Kiểm tra:** liên kết Markdown và `git diff --check` PASS sau cập nhật.

**Commit đề xuất:** `docs: record successful full regression suite`

## 057 - 2026-10-13 - Triển khai Spark ETL và DL4J CPU Forecast Service

**Mục tiêu:** tiếp tục hoàn thiện yêu cầu/checklist bằng luồng Bronze → Silver/Gold và inference thật.

**Thay đổi:** thêm Spark ETL/parity jobs, quality gates cho raw/split/window counts, profile runtime và
tài liệu 03. Thêm request validation, CPU service health/metadata/forecast, profile `cpu-service`,
Dockerfile và tài liệu 04; Compose mount dataset/model read-only và cấp 2 GiB cho service.

**Kết quả:** ETL chạy thật trên hai workers, đúng toàn bộ count và parity. Forecast container dùng
`CpuBackend`, load đúng hash seed 123, trả đủ H1-H7 và parity 21 điểm với artifact GPU. Checklist tăng
từ 4/12 lên 10/12 acceptance criteria đạt. Không training hoặc thay artifact.

**TDD/kiểm tra:** các vòng RED/GREEN được ghi trong lịch sử; Spark package PASS 3/3; Compose config,
health, invalid request và runtime parity PASS. Full root `mvn -B test` PASS 64/64, 0 failure/error/skip,
`BUILD SUCCESS` trong 03:17 trên JCublasBackend/RTX 4050. Markdown links và `git diff --check` được
kiểm tra cuối.

**Giới hạn:** Spring Boot API, migrations, React dashboard, E2E và benchmark 1/2 Workers còn thiếu.
CPU fat JAR 1,45 GB cần thu gọn Linux-only; S3A standard committer còn cảnh báo.

**Commit đề xuất:** `feat: run spark etl and serve forecasts on cpu`

## 058 - 2026-10-13 - Xác định bước triển khai tiếp theo

**Mục tiêu và lý do:** xác định hạng mục nên thực hiện ngay sau khi Spark ETL và Forecast Service đã
chạy thật, để tiếp tục hoàn thiện hệ thống end-to-end theo checklist thay vì tối ưu một thành phần chưa
chặn luồng nghiệp vụ.

**Kết luận:** ưu tiên Giai đoạn 5 - PostgreSQL migrations và Spring Boot Backend API. Hạng mục đầu tiên
nên gồm schema nghiệp vụ có version, dữ liệu nền Store 1/items/model, REST DTO có validation và xử lý
lỗi, client gọi Forecast Service có timeout, cùng integration tests cho migration/API. Sau khi API ổn
định mới triển khai React Dashboard lấy toàn bộ dữ liệu qua API; bước này trực tiếp mở khóa tiêu chí
nghiệm thu số 8.

**Thứ tự sau đó:** hoàn thành React Dashboard và browser E2E, rồi chạy benchmark Spark lặp lại với một
và hai Workers trên cùng input/logic/resource policy để đóng tiêu chí số 11. Việc thu gọn CPU fat JAR
và thay S3A committer vẫn cần làm trước production nhưng không phải blocker kế tiếp của luồng MVP.

**Thay đổi và kiểm tra:** chỉ cập nhật nhật ký định hướng; không sửa code, cấu hình, model, scaler,
checkpoint hoặc artifact và không chạy training/test chức năng. Kết luận được đối chiếu với checklist
hiện tại 10/12 tiêu chí đạt; liên kết Markdown và `git diff --check` được kiểm tra sau cập nhật.

**Commit đề xuất:** `docs: record next implementation priority`

## 059 - 2026-10-13 - Triển khai Spring Boot API và React Dashboard local

**Mục tiêu và lý do:** tiếp tục hoàn thiện prompt/checklist bằng backend và dashboard thật, ưu tiên
miễn phí, dễ chạy bằng Docker Compose trên máy cá nhân và không phụ thuộc tài khoản cloud.

**Thay đổi:** thêm module Spring Boot Java 11 với Flyway, mười bảng kể cả `sales_records`, CSV importer,
JDBC REST endpoints, OpenAPI, pagination, validation, global errors, forecast timeout và ingestion
idempotency. Thay frontend bootstrap bằng React/TypeScript/Vite sáu màn hình, SVG chart và Nginx proxy
`/api`. Thêm Dockerfile multi-stage, mount dataset read-only, tài liệu 05/06 và cập nhật kiến trúc,
README, trạng thái/checklist. Sửa frontend healthcheck dùng `127.0.0.1` vì Alpine resolve `localhost`
sang địa chỉ không được Nginx lắng nghe trong container.

**TDD và kết quả:** validation test RED 1 failure/1 error với stub, sau đó GREEN 2/2. `npm run build`
PASS; Compose config PASS. Runtime có 91.300 sales rows, 50 items, Flyway baseline+migration; backend,
frontend HTTP và OpenAPI trả 200. Forecast qua frontend/API cho Item 1 origin 2017-06-30 trả
`COMPLETED`, 7 horizon và lưu 7 rows. Item 99 trả 400; hai request ingestion cùng key trả cùng ID và
chỉ có một row. Không chạy training hoặc thay model/scaler/checkpoint.

**Giới hạn:** browser automation không khả dụng nên chưa có visual/responsive/keyboard QA. Ingest API
mới ghi `ACCEPTED`, chưa tự submit Spark; automated PostgreSQL/API/browser E2E và benchmark 1/2 Workers
còn thiếu. Root full suite 64/64 là bằng chứng lượt trước, không chạy lại trong lượt này.

**Commit đề xuất:** `feat: add local spring api and react dashboard`

## 060 - 2026-10-13 - Giải thích luồng hoạt động của hệ thống web

**Mục tiêu và lý do:** giải thích ngắn gọn cách dashboard, API, Forecast Service, PostgreSQL, Spark và
MinIO phối hợp để người dùng hiểu dữ liệu đi đâu khi xem doanh số hoặc chạy một dự báo.

**Kết quả kiểm tra:** tại thời điểm giải thích, `frontend`, `backend-api`, `forecast-service` và
`postgres` đều đang chạy healthy. Gọi `http://localhost:3000/api/health` qua reverse proxy trả
backend/database `UP`; pipeline gần nhất trả `COMPLETED`, 91.300 raw rows, 91.300 Gold rows và
`durationMillis=64695`. Đây là kiểm tra read-only, không tạo forecast hoặc pipeline run mới.

**Nội dung hướng dẫn:** mô tả hai luồng riêng: dữ liệu offline CSV → MinIO → Spark → Gold/PostgreSQL,
và luồng online trình duyệt → Nginx → Spring Boot → PostgreSQL hoặc DL4J Forecast Service → lưu kết
quả → trả dashboard. Làm rõ frontend không gọi MinIO trực tiếp và việc bấm dự báo không huấn luyện lại
mô hình.

**Thay đổi và giới hạn:** không sửa code/cấu hình/hành vi hệ thống; chỉ bổ sung nhật ký hướng dẫn và
sửa thứ tự mục lục tài liệu bị lặp số. Không chạy test chức năng vì không có thay đổi production.
Liên kết Markdown và `git diff --check` được kiểm tra sau cập nhật.

**Commit đề xuất:** `docs: explain web application flow`

## 061 - 2026-10-13 - Làm Dashboard dự báo dễ hiểu cho người mới

**Mục tiêu và lý do:** loại bỏ rào cản thuật ngữ H1-H7, Item và origin; giúp người dùng biết đang chọn
sản phẩm nào, dự báo điều gì và đọc kết quả ra sao mà không cần kiến thức ML.

**Thay đổi:** giao diện Dự báo có hướng dẫn ba bước; dùng “Ngày bắt đầu dự báo” và “Ngày dự báo 1-7”,
hiển thị ngày đích, số lượng dự kiến bán, mốc tuần trước và thực tế lịch sử. Item 1-50 được đổi thành
“Sản phẩm #N (mã ẩn danh)” kèm giải thích dataset không chứa tên hàng thật, nên không tạo tên giả.
Thêm phần bắt đầu nhanh ở Tổng quan và giải thích trục doanh số ở Phân tích.

**TDD và kết quả:** content test ban đầu RED, báo thiếu đủ sáu cụm hướng dẫn; sau triển khai PASS
`DASHBOARD_COPY_OK`. TypeScript/Vite build PASS. Image mới build/deploy thành công, frontend healthy,
HTTP 200; bundle live có đủ ba nhãn đại diện được kiểm tra. Không sửa backend/model/scaler/checkpoint,
không tạo dự báo mới và không chạy training.

**Giới hạn:** chưa có browser visual/responsive/keyboard QA vì công cụ browser không khả dụng trong
phiên; xác minh hiện tại là source test, production build, container health và HTTP bundle.

**Commit đề xuất:** `feat: simplify forecast dashboard language`
## 062 - 2026-10-13 - Giải thích đơn vị và ý nghĩa con số dự báo

### Mục tiêu và lý do

- Làm rõ cho người dùng con số trên màn hình dự báo đại diện cho đại lượng nào, có đơn vị gì và nên đọc như thế nào.
- Phân biệt kết quả mô hình với doanh thu, phần trăm, xác suất, lượng đặt hàng và tổng nhu cầu bảy ngày.

### Kết quả kiểm tra và diễn giải

- Kiểm tra chỉ đọc kết quả `COMPLETED` gần nhất của API cho `Item #47`, ngày bắt đầu dự báo `2017-06-30`.
- Trường `prediction` là **số lượng đơn vị sản phẩm dự kiến bán trong riêng ngày đích**. Đây là nhu cầu bán hàng theo đơn vị đếm, không phải tiền hay phần trăm.
- Ví dụ `H1` cho ngày `2017-07-01`: mô hình dự báo `32.8554`, tức khoảng **33 sản phẩm**; mốc `Weekly Naive` là `41` và số thực tế lịch sử `actual` là `29`. Như vậy mô hình dự báo cao hơn thực tế khoảng `3.8554` sản phẩm ở ngày này.
- Kết quả có phần thập phân vì mô hình hồi quy sinh giá trị liên tục. Giao diện giữ số gốc để đánh giá; khi diễn giải nghiệp vụ có thể làm tròn thành số sản phẩm gần nhất.
- `H1` đến `H7` là bảy ngày đích liên tiếp; mỗi giá trị thuộc một ngày, không phải tổng cộng dồn. Nếu cộng riêng bảy dự báo của kết quả này thì được khoảng `191.95`, tương đương khoảng `192` sản phẩm trong cả bảy ngày.
- `Weekly Naive` là mốc so sánh lấy doanh số của cùng thứ tuần trước. `actual` là số bán thực tế và chỉ có sau khi ngày đích đã xảy ra; dữ liệu năm 2017 cho phép hiển thị trường này để đánh giá.
- Dữ liệu hiện chỉ cung cấp mã `store_id` và `item_id`, không có tên thương mại/SKU, nên hệ thống chỉ có thể ghi `Item #47`. Muốn dùng thực tế cần thêm bảng ánh xạ `item_id -> tên/SKU` từ doanh nghiệp.
- Dự báo hỗ trợ lập kế hoạch tồn kho; nó không tự động là số lượng phải đặt hàng vì quyết định đặt hàng còn phụ thuộc tồn kho hiện có, hàng đang về, tồn kho an toàn và thời gian giao hàng.

### Phạm vi xác minh và giới hạn

- Chỉ đọc dữ liệu live hiện có; không chạy huấn luyện, không tạo dự báo mới và không thay đổi mã nguồn/cấu hình.
- Kết luận về đơn vị dựa trên trường doanh số dạng số lượng của pipeline hiện tại và bản ghi dự báo đã hoàn tất.

### Tiêu đề commit đề xuất

`docs: clarify forecast value units`

## 063 - 2026-10-13 - Tối ưu màn hình kết quả dự báo cho người dùng

**Mục tiêu và lý do:** làm màn hình dễ đọc nhất có thể bằng cách bỏ hướng dẫn theo bước/ví dụ, thay
nhãn ngày kỹ thuật bằng ngày lịch cụ thể, ghi rõ ý nghĩa và đơn vị của từng con số, dùng bố cục dọc và
bổ sung tổng nhu cầu dự báo trong tuần.

**Thay đổi:** rút màn hình Dự báo còn form chọn dữ liệu và phần kết quả. Mỗi ngày hiển thị thứ/ngày/
tháng/năm cùng ba dòng `Sản phẩm dự kiến bán`, `Số sản phẩm bán cùng ngày tuần trước` và `Số sản phẩm
thực tế đã bán`; mỗi số được làm tròn và gắn đơn vị `sản phẩm`. Thêm khối `Tổng nhu cầu dự báo trong 7
ngày`, tính từ tổng bảy prediction gốc. Thẻ ngày xếp hai cột trên desktop, một cột trên màn hình hẹp.

**TDD và kết quả:** content test mới chạy RED đúng dự kiến với sáu nội dung thiếu và năm nội dung cũ
cần bỏ. Sau triển khai, `npm test` PASS `DASHBOARD_RESULT_COPY_OK`; `npm run build` PASS. Docker Compose
build/recreate frontend thành công; container healthy, HTTP 200 và bundle live chứa đủ các nhãn kết
quả mới. Không sửa API, model, scaler, checkpoint, dữ liệu hoặc chạy huấn luyện.

**Tệp ảnh hưởng:** `frontend/src/main.tsx`, `frontend/src/forecast.css`,
`frontend/scripts/check-dashboard-copy.mjs`, output build `frontend/dist/` và tài liệu liên quan.

**Giới hạn:** trình duyệt tích hợp không khả dụng nên chưa có visual/responsive/keyboard QA thực tế;
bố cục responsive mới được xác minh ở mức source CSS và production build.

**Commit đề xuất:** `feat: clarify daily forecasts and add weekly total`

## 064 - 2026-10-13 - Làm rõ phạm vi dữ liệu của Data Lake

**Mục tiêu và lý do:** xác định Data Lake đang chứa toàn bộ dữ liệu nguồn hay chỉ các bản ghi của Cửa
hàng 1, đồng thời phân biệt chính xác dữ liệu ở Bronze với đầu ra Silver/Gold.

**Kết quả kiểm tra:** `infrastructure/minio/init-minio.sh` sao chép nguyên file `data/train.csv` vào
`s3a://bronze/store-1/train.csv`, không lọc dòng trước khi tải lên. File nguồn hiện có `913.001` dòng
tính cả header, tương ứng `913.000` bản ghi của 10 cửa hàng và dung lượng `17.333.449` byte. Trong
`Store1EtlJob.readStore1`, Spark đọc file Bronze rồi mới áp dụng `store = 1`; vì vậy Silver và Gold
chỉ chứa `91.300` bản ghi của Cửa hàng 1, gồm 50 sản phẩm và 1.826 ngày mỗi sản phẩm.

**Phát hiện giao diện:** màn hình Data Lake hiện ghi cố định `91.300` cho cả Bronze, Silver và Gold.
Con số Silver/Gold đúng với pipeline Store 1, nhưng con số Bronze không phản ánh file vật lý đang lưu:
Bronze thực tế có `913.000` bản ghi. Đường dẫn `bronze/store-1/train.csv` và metadata `storeId: 1` cũng
dễ gây hiểu nhầm vì object bên trong vẫn là file nguồn của tất cả cửa hàng.

**Thay đổi và kiểm tra:** không sửa code, cấu hình, dữ liệu hoặc giao diện vì yêu cầu hiện tại là câu
hỏi làm rõ. Đối chiếu read-only file nguồn, script nạp MinIO, mã Spark ETL và tài liệu hiện hành;
không chạy lại ETL hoặc huấn luyện.

**Giới hạn:** chưa thống kê trực tiếp object qua MinIO API trong lượt này; số dòng/byte được kiểm tra
từ đúng file local mà script init sao chép nguyên trạng. Cần một yêu cầu triển khai riêng để sửa nhãn
Dashboard hoặc đổi Bronze thành tập Store 1 thực sự.

**Commit đề xuất:** `docs: clarify bronze and store one data scope`

## 065 - 2026-10-13 - Làm biểu đồ Phân tích dễ đọc và xác định giá trị

**Mục tiêu và lý do:** giúp người dùng xác định rõ mỗi điểm trên biểu đồ thuộc ngày nào, có bao nhiêu
sản phẩm đã bán và nhanh chóng nhận biết biên độ doanh số thay vì chỉ nhìn một đường không có trục.

**Thay đổi:** thêm trục đứng `Số sản phẩm đã bán`, trục ngang `Ngày bán`, sáu mốc giá trị, năm mốc ngày,
vạch lưới và điểm dữ liệu có tooltip ngày–số lượng. Bổ sung ba thẻ `Giá trị thấp nhất`, `Trung bình mỗi
ngày`, `Giá trị cao nhất`. Biểu đồ giữ chiều rộng tối thiểu và cuộn ngang trên màn hình nhỏ. Thêm CSS
riêng tại `frontend/src/analysis.css` và mở rộng content regression test.

**TDD và kết quả:** test ban đầu RED vì thiếu tám thành phần biểu đồ yêu cầu. Sau triển khai,
`npm test` PASS `DASHBOARD_RESULT_COPY_OK` và `npm run build` PASS. Frontend image được build riêng,
container recreate thành công, healthy và trả HTTP 200; bundle live chứa đủ các nhãn mới. API live
cho Sản phẩm #1 năm 2017 có 365 ngày, min `5`, trung bình `22,2`, max `50` sản phẩm.

**Tệp ảnh hưởng:** `frontend/src/main.tsx`, `frontend/src/analysis.css`,
`frontend/scripts/check-dashboard-copy.mjs`, output `frontend/dist/` và tài liệu liên quan.

**Giới hạn:** chưa kiểm tra trực quan/responsive/bàn phím bằng trình duyệt thật vì browser tích hợp
không khả dụng. Tooltip từng điểm hiện dùng SVG title cho chuột; chưa hỗ trợ focus bàn phím từng điểm.

**Commit đề xuất:** `feat: add readable axes and values to sales chart`

## 066 - 2026-10-13 - Bổ sung phân tích ngày, tuần, tháng và nhu cầu sản phẩm

**Mục tiêu và lý do:** visual hóa lịch sử bán hàng theo nhiều mức thời gian, giúp nhận biết xu hướng,
kỳ cao điểm và sản phẩm có nhu cầu lớn trong từng tháng bằng các phương pháp phân tích mô tả dễ đọc.

**Thay đổi backend:** thêm `GET /api/analytics/monthly-demand?year=&limit=`. PostgreSQL tổng hợp tổng,
trung bình ngày, đỉnh ngày theo sản phẩm-tháng và dùng window function xếp hạng top N trong mỗi tháng.
Validate năm 2013-2017 và limit 1-50. Thêm `ApiControllerAnalyticsTest` cho kết quả và HTTP 400.

**Thay đổi frontend:** thêm bộ chọn Theo ngày/Tuần/Tháng; tuần tính từ thứ Hai đến Chủ Nhật, tháng
tính theo tháng lịch. Hiển thị tổng nhu cầu, trung bình theo kỳ, kỳ cao nhất, phần trăm so với kỳ trước,
biểu đồ tương ứng và tooltip. Thêm bảng top 5 sản phẩm theo tháng với tổng, trung bình/ngày, đỉnh/ngày.

**TDD và xác minh:** API test đầu tiên RED 2/2 với HTTP 404; sau triển khai focused test PASS 2/2 và
backend full module PASS 4/4. Frontend test RED vì thiếu tám thành phần mới, sau triển khai PASS
`DASHBOARD_RESULT_COPY_OK`; production build PASS. Backend/frontend image được build và recreate,
cả hai container healthy. API live qua frontend trả 60 hàng cho 12 tháng; tháng 01/2017 top 5 lần lượt
là sản phẩm #15/#18/#28/#38/#13, trong đó #15 bán 2.015 sản phẩm. Bundle live chứa đủ nhãn mới.

**Tệp ảnh hưởng:** `backend-api/.../ApiController.java`, `ApiControllerAnalyticsTest.java`,
`frontend/src/main.tsx`, `frontend/src/analysis.css`, content test, build output và tài liệu 05/06.

**Giới hạn:** phân tích chỉ mô tả dữ liệu Store 1 năm 2017; không suy luận nguyên nhân, không thay thế
dự báo và không đưa ra lượng nhập hàng. Nguồn thiếu giá, doanh thu, tồn kho, khuyến mãi, danh mục và
tên SKU. Chưa có visual/responsive/keyboard QA bằng browser thật trong phiên.

**Commit đề xuất:** `feat: add weekly monthly sales analytics and rankings`

## 067 - 2026-10-13 - So sánh tổng dự báo với cùng kỳ và cập nhật gitignore

**Mục tiêu và lý do:** giúp người dùng biết nhu cầu bảy ngày dự báo tăng hay giảm so với cùng bảy ngày
tuần trước, đồng thời ngăn artifact build/runtime mới bị đưa nhầm vào Git.

**Thay đổi giao diện:** `ForecastSummary` cộng tổng bảy prediction và bảy Weekly Naive, hiển thị `Tổng
cùng kỳ tuần trước`, `Chênh lệch nhu cầu`, số sản phẩm chênh và tỷ lệ phần trăm. Trạng thái được ghi rõ
`Nhu cầu tăng`, `Nhu cầu giảm` hoặc `Nhu cầu không đổi`. Khi tổng kỳ trước bằng 0, phần trăm là `—` và
giao diện giải thích không thể chia cho 0. CSS mở rộng khối tổng và responsive một cột.

**Thay đổi `.gitignore`:** thêm `node_modules/`, `dist/`, `.vite/`, log package manager,
`*.tsbuildinfo`, `/output.zip`, PID/tmp và thư mục trạng thái `.docker-data`, MinIO, PostgreSQL,
Spark event cục bộ. Không ignore source frontend, package lock, Dockerfile, docs hoặc cấu hình Compose.
File `output.zip` đã được Git theo dõi từ trước nên quy tắc mới chỉ ngăn các lần add mới sau khi file
được bỏ khỏi index; lượt này không tự chạy `git rm --cached` để tránh thay đổi staging ngoài yêu cầu.

**TDD và kết quả:** test đầu tiên RED vì thiếu năm nội dung/điều kiện mới; sau triển khai `npm test`
PASS và `npm run build` PASS. Frontend image build/recreate thành công, container healthy. Forecast
live Item #1 origin `2017-06-30`: 7 dòng, tổng dự báo `209,051`, cùng kỳ `215`, chênh `-5,949`, giảm
`2,8%`; bundle live có đủ nhãn. `git check-ignore` xác nhận frontend node_modules/dist và Maven target.

**Tệp ảnh hưởng:** `.gitignore`, `frontend/src/main.tsx`, `frontend/src/forecast.css`, content test,
frontend build output và tài liệu dự án.

**Giới hạn:** cùng kỳ hiện được định nghĩa là cùng các thứ của tuần liền trước qua Weekly Naive. Đây
là so sánh mức nhu cầu, không giải thích nguyên nhân và không thay đổi mô hình. Chưa có visual QA.
`output.zip` vẫn đang tracked cho đến khi người dùng chủ động bỏ nó khỏi Git index.

**Commit đề xuất:** `feat: compare forecast total with prior week demand`

## 068 - 2026-10-13 - Giải thích mục Spark Benchmark

**Mục tiêu và lý do:** làm rõ màn hình Spark Benchmark dùng để đo gì, khác gì với biểu đồ bán hàng và
benchmark huấn luyện mô hình, cũng như trạng thái triển khai thực tế hiện tại.

**Kết quả kiểm tra:** mục này được thiết kế để chạy cùng một Spark ETL Store 1 trên cùng input và cùng
cấu hình logic, lần lượt với 1 Worker và 2 Workers. Các trường dự kiến gồm số worker, số dòng input,
thời gian xử lý, thông lượng dòng/giây và thời điểm đo. Mục đích là chứng minh việc tăng worker có cải
thiện tốc độ xử lý dữ liệu hay không; nó không đo độ chính xác dự báo và không phải biểu đồ doanh số.

**Trạng thái live:** Spark Master, hai Worker và job runner đều đang chạy, nhưng
`GET /api/benchmarks` trả mảng rỗng. Chưa có lượt benchmark lặp 1-vs-2 với cùng resource policy được
thực hiện và ghi vào `benchmark_runs`, nên Dashboard chủ động hiển thị `Chưa có số đo benchmark` thay
vì tạo số liệu giả. Lượt ETL hai worker 64.695 ms trước đây chỉ là một lần chạy đơn, không đủ để so sánh.

**Phân biệt:** benchmark GPU GlobalResidualAR đã có trong tài liệu nghiên cứu là phép đo huấn luyện mô
hình DL4J, hoàn toàn khác Spark Benchmark đang nói đến ở Dashboard. Không được dùng kết quả GPU đó để
điền bảng Spark.

**Thay đổi và giới hạn:** không sửa code/cấu hình/dữ liệu và không chạy benchmark nặng trong lượt giải
thích này. Đã kiểm tra read-only API, trạng thái container, mã Dashboard và checklist. Tiêu chí benchmark
Big Data vẫn chưa đạt cho tới khi có protocol lặp lại, kết quả thật và bản ghi database.

**Commit đề xuất:** `docs: explain spark benchmark purpose and status`

## 069 - 2026-10-13 - Giải thích giá trị sử dụng của Spark Benchmark

**Mục tiêu và lý do:** trả lời ngắn gọn Spark Benchmark mang lại lợi ích thực tế gì cho dự án, thay vì
chỉ mô tả các cột và cấu hình kỹ thuật.

**Kết luận:** phần này dùng để quyết định số lượng Spark Worker phù hợp khi xử lý dữ liệu. Nếu 2 Worker
nhanh hơn đáng kể so với 1 Worker trên cùng dữ liệu và tài nguyên kiểm soát, hệ thống có bằng chứng để
mở rộng xử lý song song; nếu chênh lệch nhỏ hoặc chậm hơn, không nên tốn thêm CPU/RAM. Kết quả còn là
bằng chứng đồ án rằng nền tảng Big Data đã đo khả năng scale, hỗ trợ ước lượng thời gian ETL và phát
hiện bottleneck. Nó không hỗ trợ người bán đọc nhu cầu sản phẩm hằng ngày và không đánh giá độ chính
xác của mô hình dự báo.

**Thay đổi và kiểm tra:** không sửa code/cấu hình/dữ liệu, không chạy benchmark. Đối chiếu checklist,
mã API và Dashboard hiện tại; benchmark_runs vẫn chưa có kết quả đo thật như đã ghi ở mục 068.
`git diff --check` và liên kết Markdown được kiểm tra sau khi ghi nhật ký.

**Giới hạn:** lợi ích trên chỉ trở thành bằng chứng định lượng sau khi benchmark 1-vs-2 Worker được
chạy lặp, cùng input/logic/resource policy và lưu kết quả thật.

**Commit đề xuất:** `docs: explain practical value of spark benchmark`

## 070 - 2026-10-13 - Minh họa luồng Spark Master và Worker

**Mục tiêu và lý do:** giải thích “số máy xử lý dữ liệu” bằng một luồng cụ thể, phù hợp người chưa quen
Spark, và liên hệ trực tiếp với kiến trúc Docker Compose của dự án.

**Diễn giải:** Master giống người quản lý công việc, không tự xử lý toàn bộ dữ liệu. Job runner gửi yêu
cầu ETL; Master chia các partition/task và giao cho Worker. Mỗi Worker là một tiến trình/container có
CPU và RAM riêng; trong production có thể đặt trên máy vật lý/VM khác nhau, còn dự án local hiện chạy
hai container Worker trên cùng một máy Windows. Cả hai đọc các phần dữ liệu từ MinIO, lọc Store 1,
kiểm tra và tạo feature; Spark gom kết quả rồi ghi Silver/Gold về MinIO.

**Ví dụ:** với 91.300 dòng Store 1, một Worker thực hiện tuần tự các task được giao. Khi có hai Worker,
Master có thể giao các partition khác nhau cho hai Worker xử lý đồng thời, chẳng hạn Worker 1 xử lý một
nhóm item và Worker 2 xử lý nhóm còn lại; đây chỉ là minh họa vì Spark quyết định partition/task thực
tế, không đảm bảo chia đúng 25 item mỗi bên. PostgreSQL/Dashboard chỉ nhận thống kê sau khi job hoàn tất.

**Thay đổi và kiểm tra:** không sửa code/cấu hình/dữ liệu, không chạy ETL hoặc benchmark. Đối chiếu
Compose và `Store1EtlJob`; `git diff --check` và liên kết Markdown được kiểm tra sau khi ghi nhật ký.

**Giới hạn:** hai Worker local không đồng nghĩa hai máy tính vật lý. Hiệu quả song song còn phụ thuộc
CPU/RAM máy chủ, số partition, đọc/ghi MinIO, shuffle và overhead; phải benchmark mới biết nhanh hơn bao
nhiêu.

**Commit đề xuất:** `docs: illustrate spark master worker data flow`

## 071 - 2026-10-13 - Làm rõ luồng nhập dữ liệu mới qua MinIO và Spark

**Mục tiêu và lý do:** xác nhận cách hiểu “dữ liệu mới vào MinIO rồi Spark đọc” và phân biệt kiến trúc
mục tiêu với chức năng thực sự đã tự động hóa trong MVP hiện tại.

**Kết luận kiến trúc:** đúng, trong luồng batch mong muốn, hệ thống nguồn/POS xuất dữ liệu bán hàng mới
thành file có version hoặc partition ngày, dịch vụ ingestion kiểm tra sơ bộ rồi lưu nguyên trạng vào
Bronze trên MinIO. Sau đó orchestration submit Spark ETL; Spark đọc các object Bronze chưa xử lý, kiểm
tra/lọc/chuẩn hóa/tạo feature, ghi Silver/Gold và cập nhật PostgreSQL để API/Dashboard sử dụng. Dữ liệu
Bronze nên bất biến và có metadata/idempotency để không xử lý trùng.

**Trạng thái dự án hiện tại:** chưa có màn hình nhập bán hàng mới hoặc upload API. `minio-init` chỉ sao
chép file local `data/train.csv` vào `bronze/store-1/train.csv` khi bootstrap. Spark ETL phải được submit
thủ công. `POST /api/pipelines/ingest` hiện chỉ ghi một bản ghi trạng thái `ACCEPTED` vào PostgreSQL;
không upload file, không gọi Spark và không cập nhật dashboard. Backend còn nhập Store 1 từ file CSV
mount trực tiếp khi bảng `sales_records` trống, nên luồng runtime chưa hoàn toàn lấy Gold từ MinIO.

**Thay đổi và kiểm tra:** không sửa code/cấu hình/dữ liệu, không upload hoặc chạy Spark. Đối chiếu
`init-minio.sh`, Compose, API ingest và tài liệu pipeline; `git diff --check` cùng liên kết Markdown được
kiểm tra sau khi ghi nhật ký.

**Giới hạn:** muốn nhập liệu mới tự động cần triển khai upload/connector, naming/versioning, schema
validation, idempotency, Spark orchestration, incremental ETL và bước publish/upsert PostgreSQL; các
thành phần này chưa được chứng minh hoạt động trong MVP.

**Commit đề xuất:** `docs: clarify minio ingestion and spark processing flow`

## 072 - 2026-10-13 - Xác minh trạng thái tự động ingestion MinIO-Spark

**Mục tiêu và lý do:** trả lời liệu luồng “nhập dữ liệu mới → MinIO → Spark → Dashboard” đã được triển
khai hoàn chỉnh và tự động trong hệ thống hiện tại hay chưa.

**Kết luận:** chưa hoàn chỉnh. Hạ tầng và xử lý lõi đã có: MinIO healthy, Spark Master/hai Worker/job
runner đang chạy; file bootstrap được lưu vào Bronze; Spark ETL đã từng đọc Bronze và hoàn tất một
lượt 91.300 raw/Gold rows trong 64.695 ms; backend/frontend healthy. Tuy nhiên chỉ file local
`data/train.csv` được `minio-init` sao chép khi bootstrap và ETL được submit thủ công.

**Phần còn thiếu:** chưa có form/API upload dữ liệu bán hàng mới, chưa có connector POS, chưa quản lý
file mới theo ngày/version, chưa có trigger/orchestrator tự chạy Spark và chưa có bước tự động publish
Gold mới vào PostgreSQL. `POST /api/pipelines/ingest` hiện chỉ ghi trạng thái `ACCEPTED` cùng source URI
vào database, không upload file và không gọi `spark-submit`. Backend khi database trống vẫn import CSV
mount tại `/opt/data/train.csv`.

**Phạm vi xác minh:** kiểm tra read-only mã init MinIO, API ingest, Compose, container health và pipeline
record live; không gửi ingest request, không chạy Spark và không sửa code/cấu hình/dữ liệu.

**Giới hạn:** một pipeline record `COMPLETED` chứng minh lượt ETL trước đã chạy thành công, không chứng
minh hệ thống tự nhận và xử lý file mới. Tự động hóa end-to-end vẫn là hạng mục triển khai tiếp theo.

**Commit đề xuất:** `docs: verify current ingestion automation status`

## 073 - 2026-10-13 - Đánh giá khả thi luồng ingestion tự động hoàn chỉnh

**Mục tiêu và lý do:** xác định có thể triển khai ngay luồng upload dữ liệu mới → MinIO Bronze → Spark
ETL → Silver/Gold → PostgreSQL → Dashboard trên kiến trúc local miễn phí hiện tại hay không.

**Kết luận:** có thể triển khai, không cần dịch vụ trả phí và không có blocker hạ tầng cơ bản. MinIO,
Spark Standalone, PostgreSQL, Spring Boot API, React Dashboard và Docker network đều đã có. Cách phù
hợp cho MVP là upload CSV multipart vào backend, backend kiểm tra schema/kích thước và lưu object bất
biến theo run ID trong Bronze; một orchestration service nội bộ submit `spark-submit`, theo dõi exit
code/trạng thái; Spark ghi Silver/Gold theo run; sau thành công backend transactionally upsert dữ liệu
đã kiểm chứng vào PostgreSQL và Dashboard polling trạng thái run.

**Yêu cầu an toàn/chất lượng:** cần khóa idempotency bằng checksum/run ID; không overwrite Bronze; giới
hạn file và chống path traversal; trạng thái `ACCEPTED/RUNNING/COMPLETED/FAILED`; log lỗi; chỉ publish
sau quality gate; retry không tạo trùng; một job active hoặc cơ chế queue để tránh hai ETL ghi đè cùng
prefix; giữ train-only normalization và split thời gian nếu dữ liệu mới đi vào luồng model.

**Phạm vi nên triển khai:** API upload/status, MinIO client, bảng/file metadata, Spark launcher/worker,
incremental hoặc run-scoped ETL, Gold-to-PostgreSQL publisher, giao diện upload/progress và automated
tests cho upload sai schema, idempotency, lỗi Spark, retry và publish atomic. Không cần retrain model để
hoàn thành ingestion; retraining tự động là hạng mục riêng và không nên ngầm kích hoạt.

**Thay đổi và giới hạn lượt này:** đây là đánh giá khả thi, chưa sửa code hay chạy pipeline. Việc triển
khai là thay đổi lớn có ghi external state và cần test end-to-end; không nên coi API `ACCEPTED` hiện tại
là hoàn chỉnh. `git diff --check` và liên kết Markdown được kiểm tra sau khi ghi nhật ký.

**Commit đề xuất:** `docs: assess automated ingestion implementation scope`

## 074 - 2026-10-13 - Làm rõ ranh giới model và quy trình cập nhật checkpoint

- **Mục tiêu:** Xác định luồng hiện tại có tách biệt model hay không và khả năng huấn luyện, cập nhật model mới vào hệ thống.
- **Lý do:** Cần phân biệt rõ dữ liệu bán hàng mới, Spark ETL, quá trình huấn luyện và model đang phục vụ để tránh tự động ghi đè model ổn định.
- **Kết quả kiểm tra:** Luồng ingestion/ETL/analytics được tách khỏi huấn luyện và phục vụ dự báo. `forecast-service` hiện chỉ suy luận, nạp gói triển khai seed `123` qua volume chỉ đọc tại `/opt/model`; dữ liệu mới đi qua MinIO/Spark không tự làm thay đổi trọng số model.
- **Khả năng cập nhật:** Có thể huấn luyện model mới trong thư mục run riêng, chọn ứng viên bằng Validation 2016, kiểm tra đầy đủ checkpoint, scaler, ánh xạ mặt hàng, schema đặc trưng và metadata, sau đó đổi đường dẫn gói deployment trong Docker Compose và chỉ tạo lại `forecast-service`. Có thể rollback bằng cách khôi phục đường dẫn gói model trước đó.
- **Ranh giới hiện tại:** Các script huấn luyện vẫn đọc dữ liệu cục bộ `data/train.csv`; chưa có bước tự động xuất dữ liệu Gold từ MinIO/PostgreSQL thành tập huấn luyện. Vì vậy, muốn dữ liệu nhập mới đi thẳng vào lần train kế tiếp thì cần bổ sung cầu nối dữ liệu hoặc adapter huấn luyện.
- **Quy tắc an toàn:** Giữ chia tập theo thời gian, chỉ fit chuẩn hóa trên tập train, không dùng Test 2017 để chọn model, không ghi đè artifact đang phục vụ và không chỉ thay riêng `model.zip` khi các scaler/schema đi kèm đã thay đổi.
- **Xác minh:** Đã đối chiếu cấu hình Docker Compose, tài liệu model serving và các script huấn luyện hiện có. Lượt này chỉ điều tra và cập nhật tài liệu; không chạy huấn luyện, không thay checkpoint và không khởi động lại dịch vụ.
- **Hạn chế:** Chưa thực hiện một lần promotion model mới thực tế nên trạng thái cập nhật model chỉ được xác minh ở mức kiến trúc và cấu hình nguồn.
- **Commit đề xuất:** `docs: clarify model isolation and update workflow`

## 075 - 2026-10-13 - Hoàn thiện ingestion CSV tự động end-to-end

- **Mục tiêu:** Triển khai màn hình upload CSV, MinIO Bronze, validation, checksum, tự gọi Spark, theo dõi trạng thái, publish PostgreSQL sau thành công, Dashboard refresh và retry/lỗi/trùng.
- **Lý do:** Endpoint cũ chỉ tạo bản ghi `ACCEPTED`; chưa có luồng vận hành hoàn chỉnh để người dùng nhập và phân tích dữ liệu mới.
- **Thay đổi backend:** Thêm MinIO SDK, Apache Commons CSV, validator cho header/file rỗng/ngày/số/sales âm/bản ghi trùng; SHA-256 idempotency; API multipart upload, status và retry; async worker tuần tự; `SparkSubmitter` có timeout/log; repository publish transaction sau Spark thành công. Flyway V2 bổ sung checksum, filename, error, attempt, timestamps và index.
- **Thay đổi Spark/Docker:** Thêm `IncrementalSalesEtlJob` ghi Silver/Gold/statistics theo run ID, kiểm tra quality gate và hỗ trợ overwrite an toàn khi retry. Backend image đóng gói Spark CLI cùng ETL JAR; Compose truyền MinIO/Spark config và dependency startup. `.dockerignore` cho phép build context của `spark-etl` nhưng tiếp tục loại `target`.
- **Thay đổi Dashboard:** Thêm màn hình `Nhập dữ liệu`, chọn `.csv`, mô tả schema, polling hai giây, bốn trạng thái tiếng Việt, thông báo duplicate/error, nút retry và bảng các pipeline gần đây.
- **TDD:** Check validator ban đầu RED do `SalesUploadValidator` chưa tồn tại. Sau triển khai, backend `mvn -B test` PASS 11/11; Spark `mvn -B -P spark-runtime test package` PASS 3/3; frontend `npm test` và `npm run build` PASS.
- **Xác minh runtime:** Docker build/recreate backend và frontend thành công, cả hai healthy; frontend HTTP 200. File mẫu 4 dòng tạo run `4`, đi qua `ACCEPTED -> RUNNING -> COMPLETED`, raw/gold `4/4`, attempts `1`. MinIO có Bronze CSV, Silver/Gold Parquet và statistics JSON với `_SUCCESS`; PostgreSQL tăng từ 91.300 lên 91.304 rows, max date `2018-01-02`. Upload lặp trả `duplicate=true`, cùng run `4`, không tăng attempts.
- **Ranh giới model:** Luồng ingestion không gọi training, không sửa model/scaler/checkpoint và không thay nguyên tắc split/normalization hiện có.
- **Giới hạn:** Chrome và IAB đều không khả dụng nên chưa có visual/responsive/keyboard QA. Chưa có distributed lease/recovery khi backend chết giữa Spark job; CSV tối đa 50 MB được parse trong RAM; benchmark Spark 1/2 Workers vẫn ngoài phạm vi này.
- **Tệp chính:** `backend-api/`, `spark-etl/`, `frontend/src/main.tsx`, `frontend/src/ingestion.css`, `infrastructure/backend-api/Dockerfile`, `compose.yaml`, `.dockerignore`, `.env.example`, `examples/ingestion-sample.csv` và tài liệu `docs/`.
- **Commit đề xuất:** `feat: complete csv ingestion pipeline`

## 076 - 2026-10-13 - Làm rõ khả năng xóa dữ liệu hoặc gỡ PostgreSQL

- **Mục tiêu:** Giải thích hệ thống hiện tại có thể xóa dữ liệu bán hàng hoặc gỡ database hay không, đồng thời tránh nhầm giữa xóa bản ghi, reset PostgreSQL và xóa toàn bộ volume Compose.
- **Kết luận:** Có thể reset hoặc gỡ PostgreSQL bằng thao tác Docker, nhưng hiện chưa có API/màn hình xóa dữ liệu ingestion. Không thể xóa chính xác toàn bộ dữ liệu của một run một cách an toàn vì `sales_records` chưa lưu `ingestion_run_id`; các bản ghi upload được upsert theo khóa `store_id,item_id,sales_date` và có thể đã ghi đè giá trị trước đó.
- **Ranh giới vận hành:** Xóa riêng volume PostgreSQL làm mất schema, lịch sử pipeline, forecast và sales trong database; khi container được tạo lại, Flyway tạo schema và `SalesCsvImporter` có thể tự nhập lại `data/train.csv` nếu bảng rỗng. `docker compose down -v` rộng hơn và còn xóa cả named volume MinIO, nên không phù hợp nếu chỉ muốn reset DB.
- **Ảnh hưởng nếu gỡ service:** Backend, Dashboard analytics, lịch sử ingestion và lưu forecast phụ thuộc PostgreSQL; xóa service khỏi Compose sẽ làm các phần đó ngừng hoạt động, dù MinIO và Spark vẫn có thể còn dữ liệu object.
- **Thay đổi và xác minh:** Chỉ kiểm tra read-only `compose.yaml`, schema/API và tài liệu trạng thái; không chạy lệnh xóa, không sửa code/cấu hình/dữ liệu và không dừng container.
- **Giới hạn:** Muốn có chức năng “hoàn tác file đã nhập” đúng nghĩa cần bổ sung provenance theo run và quy tắc khôi phục giá trị bị upsert; không nên triển khai bằng một lệnh `DELETE` chung.
- **Commit đề xuất:** `docs: clarify database removal boundaries`

## 077 - 2026-10-13 - Làm rõ cách loại dữ liệu mẫu trước khi dùng dữ liệu khách hàng

- **Mục tiêu:** Xác định có thể xóa file ingestion mẫu và dữ liệu `train.csv` hay không khi chuyển hệ thống từ demo sang vận hành bằng dữ liệu thật của khách hàng.
- **Kết luận file mẫu:** Có thể loại run `4` vì bốn dòng mẫu nằm ở ngày `2018-01-01..02`, ngoài dữ liệu nền kết thúc `2017-12-31`, nên trong trường hợp cụ thể này có thể xác định chính xác bốn khóa sales cần xóa. Cần đồng bộ xóa hoặc đánh dấu ở PostgreSQL, Bronze upload, Silver/Gold và statistics; chỉ xóa `examples/ingestion-sample.csv` trong repository không làm mất dữ liệu đã publish.
- **Kết luận dữ liệu train:** Có thể purge dữ liệu demo trước khi go-live, nhưng không được xóa riêng `data/train.csv` ngay trong cấu hình hiện tại. MinIO init dùng file này để tạo Bronze, backend dùng nó để bootstrap khi bảng sales rỗng và Forecast Service đọc `/opt/data/train.csv` làm lịch sử đầu vào. Checkpoint seed 123 cũng được huấn luyện/scaled/mapped theo dataset demo 50 item Store 1.
- **Ranh giới production:** Dữ liệu khách hàng cần thay toàn bộ lớp nghiệp vụ demo bằng một cutover có kiểm soát: backup, dừng ghi, reset PostgreSQL và các prefix Data Lake demo, tắt bootstrap CSV, ingest dữ liệu khách hàng, xác minh schema/chất lượng/item mapping, sau đó retrain/validate/promote model riêng. Không nên trộn dữ liệu Kaggle demo với dữ liệu khách hàng hoặc dùng checkpoint cũ như thể đã học dữ liệu mới.
- **Bằng chứng hiện tại:** PostgreSQL có `91.304` sales rows từ `2013-01-01` đến `2018-01-02`; run `1` là 91.300 dòng nền, run `4` là 4 dòng sample. Source/Compose xác nhận `train.csv` được mount cho backend và Forecast Service, đồng thời được `minio-init` sao chép vào Bronze.
- **Thay đổi:** Chỉ điều tra và cập nhật tài liệu; chưa xóa file, object, row, volume hoặc model, chưa dừng container và chưa chạy training.
- **Giới hạn:** Cơ chế rollback tổng quát theo từng upload vẫn thiếu provenance/value history; chỉ riêng sample run `4` có thể xóa chính xác vì các khóa ngày-item không chồng dữ liệu nền.
- **Commit đề xuất:** `docs: define demo-to-customer data cutover`

## 078 - 2026-10-13 - Phân biệt suy luận dữ liệu mới và huấn luyện lại model

- **Mục tiêu:** Làm rõ dữ liệu khách hàng mới có bắt buộc phải huấn luyện lại model sau mỗi lần upload hay không.
- **Kết luận:** Không huấn luyện lại sau mỗi file hoặc mỗi ngày bán hàng mới. Với đúng cửa hàng, mặt hàng và miền dữ liệu mà model đã được phê duyệt, dữ liệu mới chỉ bổ sung lịch sử đầu vào; model giữ nguyên trọng số và thực hiện suy luận để tạo dự báo mới. Huấn luyện lại là quy trình riêng theo lịch hoặc được kích hoạt khi độ chính xác suy giảm/data drift, có Validation gate, bước phê duyệt, promotion và rollback.
- **Trường hợp khách hàng hoàn toàn mới:** Cần một lần onboarding, huấn luyện hoặc hiệu chỉnh và đánh giá model trên dữ liệu lịch sử của khách hàng. Đây không phải huấn luyện lại cho từng file. Checkpoint hiện tại chỉ học Store 1, 50 item, kèm mapping và scaler train-only của chính tập demo nên không có bằng chứng để dùng trực tiếp cho doanh nghiệp, mã hàng hoặc thang nhu cầu khác.
- **Khoảng trống triển khai hiện tại:** Luồng upload đã đưa dữ liệu mới qua MinIO, Spark và PostgreSQL cho phân tích, nhưng `ForecastServiceMain` vẫn đọc lịch sử tĩnh từ `DATASET_PATH` mặc định `/opt/data/train.csv` rồi tìm window đã dựng sẵn. Vì vậy dữ liệu upload mới hiện chưa trở thành lịch sử đầu vào của lần dự báo kế tiếp; ingestion và inference mới chỉ tách biệt, chưa nối end-to-end.
- **Kiến trúc đúng hướng:** Vận hành hằng ngày theo luồng upload → kiểm tra/ETL → cập nhật lịch sử → suy luận bằng model đang được phê duyệt, không train. Quy trình định kỳ hoặc khi drift mới tạo model ứng viên → đánh giá theo thời gian và baseline → phê duyệt → thay model. Mặt hàng mới chưa có đủ lịch sử phải dùng chiến lược cold-start/baseline thay vì tự động train toàn hệ thống.
- **Lựa chọn mở rộng:** Nếu muốn một model dùng ngay cho nhiều khách hàng, cần xây dựng global model thật sự trên nhiều cửa hàng/khách hàng với đặc trưng định danh và chiến lược cold-start. Global model hiện tại chỉ global giữa 50 item của một Store 1, không phải global giữa các khách hàng.
- **Thay đổi và xác minh:** Chỉ điều tra source và cập nhật tài liệu; không sửa code, dữ liệu, scaler hoặc checkpoint, không chạy training. Đã đối chiếu `ForecastServiceMain`, cấu hình backend CSV và tài liệu trạng thái/model serving.
- **Giới hạn:** Chưa triển khai adapter để Forecast Service lấy chuỗi lịch sử mới nhất từ PostgreSQL hoặc Gold; chưa có drift monitor và quy trình retraining/promotion tự động có kiểm soát.
- **Commit đề xuất:** `docs: clarify inference and retraining lifecycle`

## 079 - 2026-10-13 - Làm rõ phạm vi một model cho cửa hàng và khách hàng

- **Mục tiêu:** Trả lời việc mỗi khách hàng hoặc mỗi cửa hàng có bắt buộc phải sở hữu một model riêng hay không.
- **Kết luận:** Không có quy tắc bắt buộc một cửa hàng bằng một model. Có thể dùng model riêng cho từng đơn vị, một global model chung cho nhiều cửa hàng, hoặc mô hình lai dùng global model chung rồi hiệu chỉnh theo từng khách hàng. Lựa chọn phụ thuộc mức độ tương đồng của ngành hàng, mã sản phẩm, quy mô và lượng lịch sử.
- **Trạng thái dự án:** Model hiện tại là `SingleStoreGlobalDataset`: một model dùng chung cho 50 item trong đúng Store 1, với item one-hot và scaler train-only riêng theo item. Nó không nhận diện nhiều store/customer và artifact ghi một `storeId`; vì vậy hiện tại chỉ nên xem đây là model của một cửa hàng demo, không phải model đa khách hàng.
- **Hướng phù hợp trước mắt:** Với khách hàng thật đầu tiên, dùng một deployment model đã onboarding và kiểm định cho khách hàng đó là phương án ít rủi ro nhất. Nếu khách hàng có nhiều chi nhánh cùng loại sản phẩm và hành vi tương đồng, có thể thiết kế một model chung cho toàn chuỗi với `customer_id/store_id/item_id`, thay vì tạo model cho từng cửa hàng.
- **Hướng mở rộng:** Khi có dữ liệu của nhiều khách hàng, đánh giá global multi-customer model và tách dữ liệu/scaler/permission theo tenant. Khách hàng hoặc mặt hàng mới thiếu lịch sử dùng baseline/cold-start; không tự động tạo và train một model mới sau mỗi lần upload.
- **Thay đổi và xác minh:** Chỉ đối chiếu source và cập nhật tài liệu; không sửa code, schema, dữ liệu, model, scaler hoặc checkpoint và không chạy training. `SingleStoreGlobalDataset` hiện từ chối panel khác store và deployment metadata lưu đúng một `storeId`.
- **Giới hạn:** Dự án chưa có tenant/customer schema, store identity feature, multi-store training, model registry theo khách hàng hoặc kiểm thử cô lập tenant. Do đó lựa chọn global đa khách hàng mới là định hướng, chưa phải chức năng đã triển khai.
- **Commit đề xuất:** `docs: clarify model scope across customers`

## 080 - 2026-10-13 - Đánh giá khả năng theo dõi tiến độ ingestion

- **Mục tiêu:** Xác định người dùng hiện có thể xem file upload đang xử lý đến bước nào, mất bao lâu và đạt tốc độ bao nhiêu hay chưa.
- **Kết quả hiện tại:** Dashboard polling API mỗi hai giây và hiển thị tên file, số bản ghi, số lần chạy, lỗi/retry cùng ba mốc `Đã tiếp nhận`, `Đang xử lý bằng Spark`, `Hoàn thành` hoặc `Thất bại`. API lưu thời điểm bắt đầu/kết thúc; `pipeline_runs` có raw rows, gold rows và tổng `duration_ms`, nên sau khi kết thúc đã có dữ liệu nền để trình bày thời gian và throughput trung bình.
- **Phần chưa có:** Khi status là `RUNNING`, toàn bộ Spark ETL vẫn chỉ là một bước; chưa có các stage chi tiết như tải MinIO, kiểm tra dữ liệu, đọc Bronze, biến đổi, ghi Silver, ghi Gold và publish PostgreSQL. Chưa lưu phần trăm, bytes đã upload, rows/giây theo thời gian, ETA, Spark application/job/stage ID hoặc log live. File log tạm của `SparkSubmitter` chỉ được đọc khi process kết thúc rồi bị xóa, nên Dashboard không thể theo dõi log Spark trực tiếp.
- **Hướng triển khai đúng:** Bổ sung stage/event bền vững với timestamp và row counters; API progress riêng; Dashboard timeline, thời gian đã chạy, thanh tiến độ và tốc độ. Phần upload có thể đo byte thực tế. Phần Spark nên lấy stage/counter thật từ event/status do job phát ra hoặc Spark status API; không dùng phần trăm giả chỉ dựa trên thời gian. Sau hoàn thành tính `gold_rows / duration` và hiển thị thời lượng từng bước.
- **Các bước nghiệp vụ đề xuất:** `UPLOAD` → `VALIDATE` → `STORE_BRONZE` → `SPARK_READ` → `SPARK_TRANSFORM` → `WRITE_SILVER_GOLD` → `PUBLISH_POSTGRESQL` → `REFRESH_DASHBOARD`. Mỗi bước có trạng thái chờ/đang chạy/hoàn thành/thất bại, bắt đầu, kết thúc, số bản ghi và thông báo lỗi.
- **Thay đổi và xác minh:** Chỉ điều tra source/API/schema/giao diện và cập nhật tài liệu; không sửa chức năng, database, container hoặc dữ liệu và không chạy test chức năng. Đã đối chiếu polling frontend, `IngestionRunRepository`, `SparkSubmitter`, migration và statistics của Spark job.
- **Giới hạn:** Đây là đánh giá khả thi, chưa phải chức năng giám sát chi tiết đã triển khai. Hiện người dùng chỉ xem được trạng thái tổng quát ba bước; tốc độ và tiến độ chi tiết chưa xuất hiện trên web.
- **Commit đề xuất:** `docs: assess ingestion progress observability`

## 081 - 2026-10-13 - Nâng cấp giao diện theo dõi tiến độ ingestion

- **Mục tiêu:** Hiển thị file upload đang đến bước nào, phần trăm, số bản ghi, thời gian và tốc độ xử lý trên Dashboard bằng dữ liệu tiến độ thật.
- **TDD:** Backend parser test và frontend content check ban đầu RED do chưa có progress marker/parser và các nhãn giao diện. Sau triển khai, focused backend PASS 5/5; full backend PASS 13/13; Spark PASS 3/3; frontend `npm test` và `npm run build` PASS.
- **Backend/database:** Flyway V3 thêm `current_stage`, `processed_rows` và `ingestion_stage_events` theo run/attempt. Repository lưu stage, trạng thái, row counters, started/finished time và lỗi; API chi tiết run trả timeline cùng raw/gold/duration.
- **Spark:** `IncrementalSalesEtlJob` phát `INGESTION_PROGRESS` sau đọc + quality gate, sau Silver và sau Gold. `SparkSubmitter` đọc stdout khi process còn chạy, parse marker và callback cập nhật database trong khi vẫn giữ timeout cùng log lỗi giới hạn.
- **Dashboard:** Thay ba ô trạng thái bằng timeline bảy bước, thanh phần trăm, elapsed time, throughput trung bình, processed/total rows và trạng thái từng bước; bổ sung bố cục responsive một cột.
- **Runtime:** Build/recreate image backend và frontend thành công; cả hai container healthy, API health UP và Dashboard HTTP 200. Run `5` được quan sát giữa chừng ở `SPARK_READ/RUNNING`, sau đó hoàn thành 7/7 stage với raw/gold `3/3`, duration `29.731 ms` và timestamp riêng từng bước. File smoke tạm đã xóa khỏi repository; ba dòng runtime vẫn là dữ liệu ingestion đã publish.
- **Model:** Không gọi training, không sửa model/scaler/checkpoint và không thay train-only normalization hoặc split thời gian.
- **Giới hạn:** IAB báo không khả dụng nên chưa có visual/keyboard QA. Timeline chi tiết hiện chỉ hiển thị cho file vừa upload trong state của phiên; bảng pipeline lịch sử chưa có nút mở lại chi tiết. Throughput là trung bình bản ghi/giây, không phải tốc độ CPU/network tức thời.
- **Commit đề xuất:** `feat: add live ingestion progress tracking`

## 082 - 2026-10-13 - Thêm CSV mẫu để thử luồng upload

- **Mục tiêu:** Cung cấp một file CSV mới để người dùng tự upload và quan sát timeline xử lý chi tiết trên Dashboard.
- **Thay đổi:** Thêm `examples/ingestion-upload-sample.csv` gồm đúng bốn cột `date,store,item,sales`, 21 bản ghi của Store 1, ba item 1-3 trong bảy ngày từ 2018-01-04 đến 2018-01-10. Nội dung khác checksum các file smoke trước để không bị idempotency trả về run cũ.
- **Kiểm tra:** Header khớp tuyệt đối; 21/21 dòng đọc được; không có ngày sai, store/item ngoài phạm vi, sales âm hoặc khóa `date,store,item` trùng. File có kích thước 400 byte. Công cụ artifact spreadsheet không khởi tạo được kernel sau một lần retry nên kiểm tra được thực hiện trực tiếp bằng parser CSV PowerShell.
- **Phạm vi:** Chỉ tạo file mẫu trong repository; chưa upload file, chưa ghi MinIO/PostgreSQL, chưa gọi Spark và không thay model/training.
- **Giới hạn:** Các giá trị sales là dữ liệu minh họa để kiểm tra kỹ thuật, không đại diện dữ liệu kinh doanh thật. Sau khi upload, checksum sẽ ngăn xử lý lại chính file không thay đổi.
- **Commit đề xuất:** `test: add ingestion upload sample csv`

## 083 - 2026-10-13 - Phân tích báo cáo luồng triển khai hoạt động của Frontend Docker

- **Mục tiêu:** Phân tích toàn diện luồng triển khai, cấu trúc multi-stage build, cơ chế Nginx reverse proxy, điều phối mạng và luồng tương tác của container frontend Docker trong dự án hiện tại, tách biệt hoàn toàn với luồng huấn luyện mô hình.
- **Phát hiện và kiến trúc:** Container `sales-demand-forecasting-frontend-1` được build từ `frontend/Dockerfile` theo mô hình multi-stage (Stage 1: `node:20-alpine` biên dịch TypeScript/React/Vite; Stage 2: `nginx:1.27-alpine` phục vụ file tĩnh và proxy `/api/` tới `backend-api:8084`). Frontend độc lập hoàn toàn với quá trình train model: không nạp DL4J, không chứa weights, không trigger training; các thao tác dự báo chỉ là request suy luận (inference) gửi qua API.
- **Thay đổi và xác minh:** Chỉ điều tra hiện trạng mã nguồn, cấu hình Docker Compose và kiểm tra runtime qua command line; không sửa đổi mã nguồn, cấu hình container hoặc dữ liệu. Container frontend hiện đang hoạt động `Up (healthy)`, cổng host 3000 ánh xạ cổng 80 container, endpoint `http://localhost:3000/` trả HTTP 200 và proxy `http://localhost:3000/api/health` trả HTTP 200 (`UP`).
- **Giới hạn:** Chưa cấu hình HTTPS/TLS trên Nginx cục bộ; chưa có kiểm thử visual/browser tự động bằng Selenium/Playwright trong CI; container frontend-bootstrap cũ trong `infrastructure/frontend-bootstrap/` vẫn tồn tại như tài liệu tham khảo lịch sử.
- **Commit đề xuất:** `docs: report frontend docker deployment workflow`

## 084 - 2026-10-14 - Xóa ba lần upload mẫu khỏi PostgreSQL

- **Mục tiêu:** Xóa khỏi database ba mẫu upload hiển thị trên Dashboard: ingestion run `4`, `5`, `6` và pipeline `2`, `3`, `4`.
- **Kiểm tra trước thao tác:** Ba run đều `COMPLETED`, khớp đúng filename/checksum và lần lượt có 4, 3, 21 dòng. PostgreSQL có đúng 28 sales rows trong khoảng 2018-01-01..2018-01-10; không có forecast request nào dùng origin trong khoảng này; có 14 stage events liên quan.
- **Thực hiện:** Trong một transaction, xóa đúng 28 `sales_records` của Store 1 trong khoảng ngày mẫu, ba `pipeline_runs`, rồi ba `data_ingestion_runs`; stage events được xóa theo `ON DELETE CASCADE`.
- **Kết quả xác minh:** Transaction commit thành công với `DELETE 28`, `DELETE 3`, `DELETE 3`. Truy vấn sau xóa trả 0 run, 0 pipeline, 0 stage và 0 sales row mẫu. Database còn đúng 91.300 sales rows, `MAX(sales_date)=2017-12-31`; API còn một pipeline nền và không còn run `4-6`.
- **Phạm vi:** Không xóa hai file CSV mẫu trong repository, không xóa object Bronze/Silver/Gold/statistics trên MinIO, không sửa code và không tác động model/training.
- **Khôi phục:** Không tạo backup riêng trước thao tác vì đây là dữ liệu mẫu đã được đối chiếu chính xác; có thể tạo lại DB rows bằng cách upload/chạy lại các CSV còn giữ hoặc khôi phục volume backup nếu có.
- **Commit đề xuất:** `docs: record sample ingestion database cleanup`

## 085 - 2026-10-14 - Dự báo đồng thời 50 sản phẩm trên trang Phân tích

- **Mục tiêu:** Chọn một ngày và dự báo đủ 50 sản phẩm trong một lượt; từng sản phẩm có tổng dự báo, cùng kỳ, chênh lệch, khoảng ngày và chi tiết bảy ngày.
- **TDD:** Backend/frontend test ban đầu RED. Sau triển khai backend PASS 14/14; frontend `npm test` và `npm run build` PASS.
- **Triển khai:** Thêm batch API ở Forecast Service và Spring Boot; DL4J evaluate 50 mẫu một lượt, PostgreSQL ghi 50 request/350 result trong transaction. Dashboard thêm danh sách responsive và mở/thu gọn chi tiết.
- **Runtime:** Ba image build thành công và container healthy. Origin 2017-06-30 trả đủ 50 sản phẩm trong 4,18 giây; mỗi request có 7 result. Frontend HTTP 200, bundle live chứa chức năng mới.
- **Giới hạn:** Chưa có browser visual/keyboard QA; sản phẩm dùng mã ẩn danh. Không retrain hoặc thay model/checkpoint/preprocessing/split.
- **Commit đề xuất:** `feat: add batch forecasts for all products`

## 086 - 2026-10-14 - Hiển thị lựa chọn tất cả sản phẩm ngay trên màn hình Dự báo

- **Mục tiêu:** Sửa việc người dùng vào phần Dự báo nhưng không thấy cách chọn toàn bộ 50 sản phẩm.
- **Lý do:** Batch forecast trước đó nằm ở khối riêng trên trang Phân tích; biểu mẫu Dự báo vẫn chỉ thể hiện luồng một sản phẩm nên khả năng tìm thấy chức năng không đạt yêu cầu.
- **TDD:** Frontend content test RED vì thiếu trường phạm vi và option tất cả; sau thay đổi `npm test` PASS `DASHBOARD_RESULT_COPY_OK`, `npm run build` PASS.
- **Thay đổi:** Thêm `Phạm vi sản phẩm`, mặc định `Tất cả 50 sản phẩm`, tùy chọn `Một sản phẩm`; ẩn mã sản phẩm trong chế độ tất cả, gọi batch API và hiển thị danh sách 50 sản phẩm có drill-down bảy ngày ngay tại màn hình Dự báo.
- **Runtime:** Frontend image build/recreate thành công, container healthy, HTTP 200; live bundle `index-BA5iD_0e.js` chứa đủ nhãn phạm vi, lựa chọn tất cả và nút chạy tất cả.
- **Giới hạn:** Vẫn cần origin date vì đó là mốc thời gian của forecast; chưa có visual/keyboard QA bằng browser thật. Không sửa backend/model/training.
- **Commit đề xuất:** `fix: expose all-products forecast selection`
