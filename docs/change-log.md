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
