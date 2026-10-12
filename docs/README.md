# Tài liệu quá trình thực hiện đồ án

Đồ án: **Dự báo nhu cầu bán hàng bằng DeepLearning4J**.

Đọc theo thứ tự:

1. [Lịch sử triển khai](implementation-history.md): từng bước, nhu cầu, thay đổi và kết quả.
2. [Trạng thái hiện tại](current-state.md): pipeline, giao diện các lớp, số liệu và giới hạn.
3. [Nhật ký yêu cầu](change-log.md): cập nhật sau mỗi yêu cầu, bằng chứng kiểm tra và tên commit đề xuất.
4. [Thí nghiệm Residual AR](residual-experiment.md): thiết kế, protocol, artifact và kết quả full 180 fits.
5. [Thí nghiệm ResidualAR Huber](residual-huber-experiment.md): full 120 fits đã hoàn tất; Huber gần bằng MSE, chưa vượt Weekly.
6. [Một store, global model nhiều items](single-store-global-experiment.md): pipeline ứng dụng riêng, Store 1/50 items, một model mỗi seed; full chưa chạy.

Hướng dẫn cài đặt/chạy nằm ở [README gốc](../README.md).
Các số liệu hiện tại lấy từ dataset cục bộ `data/train.csv`; file dữ liệu và báo cáo sinh ra không được phân phối cùng tài liệu.

## Quy tắc cập nhật

Sau mỗi yêu cầu hoàn tất, cập nhật nhật ký; khi có thay đổi chức năng hoặc quyết định kỹ thuật,
cập nhật thêm lịch sử triển khai và trạng thái hiện tại. Không ghi kết quả chưa kiểm chứng là thành công.
Tên commit trong tài liệu là đề xuất, không đồng nghĩa đã commit/push.

Quy trình cho trợ lý được khai báo tại [AGENTS.md](../AGENTS.md) và
[skill sales-project-progress](../.agents/skills/sales-project-progress/SKILL.md).
Đây là hướng dẫn cho agent khi làm việc trong repository, không phải chương trình tự chạy sau thao tác IDE/Git.
Với tác vụ huấn luyện dài, skill yêu cầu lưu đầy đủ log nhưng chỉ đọc và tổng hợp sau khi tiến trình kết thúc;
agent không liên tục đọc kết quả của từng epoch khi tiến trình vẫn hoạt động bình thường.

Các workflow kỹ thuật project-native được tách thành ba skill tự khám phá:

- [debugging-and-error-recovery](../.agents/skills/debugging-and-error-recovery/SKILL.md) cho chẩn đoán nguyên nhân gốc;
- [test-driven-development](../.agents/skills/test-driven-development/SKILL.md) cho thay đổi hành vi có regression test;
- [code-review-and-quality](../.agents/skills/code-review-and-quality/SKILL.md) cho review trước merge.

Các skill này được điều chỉnh cho Java 11, Maven, DL4J và các bất biến dữ liệu của dự án; chúng không tự cấp quyền
sửa code, chạy thí nghiệm dài, nâng dependency, commit hoặc push ngoài phạm vi yêu cầu của người dùng.
