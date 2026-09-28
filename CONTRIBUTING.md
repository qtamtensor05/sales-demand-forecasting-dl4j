# Đóng góp

## Báo lỗi hoặc đề xuất

Mở GitHub Issue và mô tả hành vi mong muốn, hành vi thực tế, phiên bản Java/Maven
và các bước tái hiện. Nếu cần dữ liệu minh họa, dùng CSV nhỏ tự tạo có header
`date,store,item,sales`.

Không đưa mật khẩu, token hoặc dữ liệu bán hàng riêng tư vào issue, log hoặc pull request.

## Gửi thay đổi

1. Tạo nhánh riêng cho thay đổi.
2. Giữ mã tương thích Java 11 và cập nhật README khi thay đổi cách sử dụng.
3. Chạy `mvn test`. Dùng `examples/train.csv` để thử riêng analyzer;
   chạy toàn bộ `Main` cần dữ liệu 2013–2017 tại `data/train.csv`.
4. Nếu sửa logic thống kê, kiểm tra thêm ngày thiếu, ngày trùng, sales bằng 0
   và dữ liệu không theo thứ tự ngày.
5. Gửi pull request mô tả thay đổi và kết quả kiểm tra.

Kiểm thử `TimeSeriesAnalyzer` và `SalesPreprocessor` dùng CSV tự tạo, không cần dataset cục bộ.
Không commit thư mục build, dataset cục bộ, báo cáo sinh ra hoặc model đã huấn luyện.

Sau mỗi yêu cầu hoàn tất, cập nhật [nhật ký](docs/change-log.md), các tài liệu tiến trình
liên quan và tên commit đề xuất theo [quy trình dự án](AGENTS.md).
