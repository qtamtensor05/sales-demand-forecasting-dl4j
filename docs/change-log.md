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
