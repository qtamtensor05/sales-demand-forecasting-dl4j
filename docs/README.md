# Tài liệu quá trình thực hiện đồ án

Đồ án: **Dự báo nhu cầu bán hàng bằng DeepLearning4J**.

Đọc theo thứ tự:

1. [Lịch sử triển khai](implementation-history.md): từng bước, nhu cầu, thay đổi và kết quả.
2. [Trạng thái hiện tại](current-state.md): pipeline, giao diện các lớp, số liệu và giới hạn.
3. [Nhật ký yêu cầu](change-log.md): cập nhật sau mỗi yêu cầu, bằng chứng kiểm tra và tên commit đề xuất.

Hướng dẫn cài đặt/chạy nằm ở [README gốc](../README.md).
Các số liệu hiện tại lấy từ dataset cục bộ `data/train.csv`; file dữ liệu và báo cáo sinh ra không được phân phối cùng tài liệu.

## Quy tắc cập nhật

Sau mỗi yêu cầu hoàn tất, cập nhật nhật ký; khi có thay đổi chức năng hoặc quyết định kỹ thuật,
cập nhật thêm lịch sử triển khai và trạng thái hiện tại. Không ghi kết quả chưa kiểm chứng là thành công.
Tên commit trong tài liệu là đề xuất, không đồng nghĩa đã commit/push.

Quy trình cho trợ lý được khai báo tại [AGENTS.md](../AGENTS.md) và
[skill sales-project-progress](../.agents/skills/sales-project-progress/SKILL.md).
Đây là hướng dẫn cho agent khi làm việc trong repository, không phải chương trình tự chạy sau thao tác IDE/Git.
