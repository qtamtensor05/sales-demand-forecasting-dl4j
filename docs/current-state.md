# Trạng thái hiện tại

## Pipeline

```text
data/train.csv
  ├─ SalesDataAnalyzer → output/sales-statistics.txt
  └─ MultiSeriesExperimentRunner
       ├─ phân tầng tertile theo mean sales giai đoạn train
       ├─ TimeSeriesAnalyzer → SalesPreprocessor riêng cho mỗi chuỗi
       └─ Weekly Naive + Direct LSTM + Autoregressive LSTM
            ├─ 5 seed; macro-average theo 12 chuỗi trên validation; không đọc test
            └─ ErrorAnalysisRunner → output/error-analysis-validation.txt
```

`Main` hiện gọi `MultiSeriesExperimentRunner`. Runner xếp hạng các cặp bằng mean sales mỗi ngày
trên train (2013–2015), chia ba tertile LOW/MEDIUM/HIGH và chọn mặc định bốn quantile đại diện mỗi
nhóm. Mỗi chuỗi dùng scaler fit riêng trên train, cấu hình 32 units / learning rate 0,001, và cùng
năm seed 42, 123, 2026, 7, 99 cho Direct lẫn Autoregressive. Không tune lại cấu hình theo chuỗi/seed;
weekly naive tính một lần trên validation mỗi chuỗi. Macro trước hết lấy trung bình năm seed trong
từng chuỗi, sau đó lấy trung bình không trọng số giữa 12 chuỗi. Không đọc test.

`ExperimentRunner` giữ toàn bộ validation metric của Direct và Autoregressive theo từng horizon
cho mỗi seed. `ErrorAnalysisRunner` tổng hợp MAE/RMSE theo nhóm LOW/MEDIUM/HIGH, horizon 1–7 và
seed; báo mean ± sample standard deviation, phần trăm cải thiện so với weekly naive và số chuỗi
thắng baseline theo RMSE. Neural metric được bình quân seed trong từng chuỗi trước khi macro-average
không trọng số giữa chuỗi. `Main` ghi báo cáo UTF-8 vào `output/error-analysis-validation.txt` sau
khi lượt multi-series kết thúc; hệ thống phân tích không huấn luyện thêm và không đọc test 2017.

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

Bước kế tiếp: khi chấp nhận chi phí, chạy lại lệnh mặc định để tạo breakdown horizon/seed đầy đủ.
Lượt tương đương trước gồm 120 fits và mất khoảng 2 giờ 05 phút; cần lưu trọn log và chờ kết thúc
theo quiet-log workflow. Chỉ sau khi đọc breakdown mới kiểm chứng giả thuyết rằng loss MSE trên
sales tuyệt đối khiến chuỗi MEDIUM/HIGH và các đỉnh nhu cầu chi phối gradient; một thử nghiệm phù
hợp sau đó là loss/target theo tỷ lệ hoặc variance-stabilizing transform fit train-only, so sánh
trên đúng validation protocol. Test 2017 không được dùng để chọn giải pháp.

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
