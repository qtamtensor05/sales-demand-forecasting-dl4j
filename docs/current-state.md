# Trạng thái hiện tại

## Pipeline

```text
data/train.csv
  ├─ SalesDataAnalyzer → output/sales-statistics.txt
  └─ TimeSeriesAnalyzer(store=1, item=1)
       ↓
     SalesPreprocessor (causal lag/rolling/calendar features; scaler fit trên train)
       ↓
     WeeklyNaiveBaseline + SalesLstmForecaster
       ├─ direct 7-output LSTM: grid cấu hình và chọn epoch bằng validation
       └─ autoregressive LSTM: one-step fit, rollout 7 ngày và chọn epoch bằng validation
            └─ so sánh cả hai với weekly naive; Main không tính metric test
```

`Main` hiện gọi `ExperimentRunner` cho store 1 + item 1. Runner cố định cấu hình 32 units,
learning rate 0,001 đã chọn trên validation ở bước trước, rồi lặp Direct và Autoregressive với seed
42, 123, 2026, 7, 99. Cấu hình không tune lại theo từng seed. Mọi metric runner chỉ dùng validation;
weekly naive là mốc cố định và không tính test.

Cấu hình LSTM dùng 30 ngày đầu vào, dự báo trực tiếp
7 ngày, Adam và seed cố định. Mỗi ngày input có 6 kênh: sales, lag-7, rolling mean 7/14 ngày,
day-of-week sin/cos. Feature sales chỉ sử dụng ngày hiện tại/quá khứ; 14 ngày warm-up đầu không
được đưa vào cửa sổ. Tensor input `[batch, 6, 30]`, labels `[batch, 7, 30]`, mask `[batch, 30]`.
Grid thử (hidden units, learning rate): (16, 0,001), (32, 0,001),
(32, 0,0003), tối đa 30 epoch mỗi ứng viên, patience 5. Model checkpoint tốt nhất trong từng ứng
viên và ứng viên thắng đều được chọn theo RMSE validation. Fit/search không dùng dữ liệu test; lượt
chạy hiện tại không tính metric test. Autoregressive được fit với cấu hình thắng của direct model
(32 units, 0,001) để so sánh kiến trúc có kiểm soát; mỗi bước dự báo một sales value,
tạo lại lag/rolling/calendar features và
dùng giá trị dự báo làm lịch sử cho bước kế tiếp. Checkpoint AR được chọn bằng RMSE rollout 7 ngày.
`Main` hiện so sánh trên validation; lần chạy này không tính metric test.

## Kết quả multi-seed validation gần nhất

Dataset cục bộ: 913.000 dòng, 500 cặp store-item, 1.826 ngày mỗi cặp, tổng sales 47.704.512.
Train 2013–2015 (1.046 windows sau warm-up), validation 2016 (360 windows), test 2017 (359 windows).
Train mất 13 target windows đầu vì cần đủ 14 ngày feature history trước cửa sổ input 30 ngày.
Scaler Min–Max fit trên train: min 4, max 43. Mọi chỉ số dự báo dưới đây ở đơn vị sales gốc.

ExperimentRunner cố định cấu hình đã chọn trước đó: 32 units, learning rate 0,001, tối đa 30 epoch,
patience 5; cùng seed được truyền cho Direct và AR. Mỗi mô hình chọn checkpoint theo RMSE validation
của chính mô hình. Mỗi seed có 2.520 dự báo cuốn chiếu chồng lấn. Độ lệch chuẩn bên dưới là độ lệch
chuẩn mẫu (n−1), metric sales ở đơn vị gốc.

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

Các kiểm thử bao gồm chia cửa sổ, kiểm tra analyzer, weekly baseline, direct LSTM, autoregressive
rollout 7 bước và thống kê sample standard deviation. `mvn test` đạt 13 tests; `mvn -B compile
exec:java` chạy đủ 10 lượt LSTM và tổng hợp validation trên `data/train.csv`, `BUILD SUCCESS` sau
15:17 phút; không tính metric test.
Kết quả chi tiết trong [nhật ký](change-log.md).

Mô hình mới thử trên một chuỗi; dù đã lặp năm seed, vẫn chưa đánh giá trên các store-item khác,
chưa có kiểm định thống kê, và AR vẫn dùng units/rate được chọn trước bằng Direct. Việc chọn epoch
bằng validation ở mỗi lượt cũng có nghĩa validation là dữ liệu tuning. Test 2017 đã được quan sát
trong các bước trước; lượt chạy này không đọc test.

Bước kế tiếp: ghi nhận AR có RMSE validation trung bình thấp nhất nhưng lợi thế nhỏ và không thắng
đa số seed; chưa chốt kiến trúc tổng quát. Mở rộng đánh giá có kiểm soát sang nhiều cặp store-item,
giữ test 2017 ngoài quá trình chọn. Cần thống nhất cách tổng hợp nhiều chuỗi (macro theo chuỗi hoặc
gộp mọi dự báo) trước khi báo cáo kết quả.
