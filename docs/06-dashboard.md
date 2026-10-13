# 06. React Dashboard

Dashboard dùng React 18, TypeScript và Vite, build bằng Node Alpine rồi phục vụ bằng Nginx. Các thư
viện đều miễn phí/mã nguồn mở; bản chạy không tải font hoặc tài nguyên CDN nên dùng được offline sau
khi image đã build.

Mở `http://localhost:3000`. Sáu màn hình gồm Tổng quan, Data Lake, Phân tích, Dự báo, Đánh giá và
Spark Benchmark. Doanh số năm 2017 được lấy từ PostgreSQL qua API và vẽ bằng SVG nhẹ. Màn hình dự
báo cho chọn mã sản phẩm 1-50 và ngày bắt đầu, gọi Spring Boot API, rồi hiển thị bảy thẻ
“Ngày dự báo 1” đến “Ngày dự báo 7” cùng ngày cụ thể, số lượng dự kiến bán và thời gian tính.
Benchmark không có số đo sẽ hiện rõ “Chưa có dữ liệu thực tế”, không dùng mock production.

Dataset không chứa tên/SKU/mô tả hàng hóa, chỉ có `item` từ 1 đến 50. Dashboard vì vậy dùng nhãn
“Sản phẩm #N (mã ẩn danh)” và giải thích giới hạn này thay vì tự bịa tên. `H1` nghĩa là ngày đầu tiên
sau ngày bắt đầu dự báo, `H2` là ngày thứ hai và tương tự đến `H7`. Giao diện không còn yêu cầu người
dùng hiểu ký hiệu H1-H7: mỗi thẻ kết quả giải thích prediction là số lượng dự kiến bán, Weekly Naive
là mốc cùng thứ tuần trước, còn Actual là số bán thật chỉ biết được vì dữ liệu 2017 là lịch sử.

Build `npm run build` đã PASS. Container frontend phục vụ HTTP 200 và request
`http://localhost:3000/api/health` trả backend/database `UP`, chứng minh reverse proxy và ranh giới
frontend → API. Công cụ browser của phiên triển khai không khả dụng nên chưa có xác minh trực quan,
responsive hoặc thao tác bàn phím; HTTP end-to-end không thay thế browser QA.

Sau cải tiến nội dung dễ hiểu, `npm test` kiểm tra sáu cụm hướng dẫn bắt buộc và PASS
`DASHBOARD_COPY_OK`; TypeScript/Vite build PASS. Image frontend mới được triển khai, container healthy,
HTTP 200 và bundle đang phục vụ có đủ nhãn “Ngày dự báo 1”, “Mã sản phẩm ẩn danh” và
“Số lượng dự kiến bán”.

## Cách hiển thị kết quả hiện tại

Màn hình Dự báo tập trung vào kết quả nghiệp vụ và không còn khối hướng dẫn theo bước hoặc ví dụ kỹ
thuật. Mỗi thẻ dùng ngày lịch đầy đủ, chẳng hạn `Thứ Bảy, 01/07/2017`, rồi trình bày theo chiều dọc
ba giá trị có đơn vị rõ ràng: số sản phẩm dự kiến bán, số sản phẩm bán cùng ngày tuần trước và số sản
phẩm thực tế đã bán. Giá trị mô hình được làm tròn về số sản phẩm gần nhất để người dùng dễ đọc.

Phía trên danh sách ngày có tổng nhu cầu dự báo trong bảy ngày. Tổng được tính từ bảy giá trị dự báo
gốc rồi làm tròn một lần khi hiển thị; đây là tổng nhu cầu dự kiến, không phải doanh thu hoặc số lượng
tự động phải đặt hàng. Bố cục dùng hai cột trên màn hình rộng và một cột trên màn hình hẹp.

## Biểu đồ phân tích doanh số

Màn hình Phân tích hiển thị ba số tóm tắt của sản phẩm đang chọn: thấp nhất, trung bình mỗi ngày và
cao nhất. Biểu đồ đường có trục đứng `Số sản phẩm đã bán`, trục ngang `Ngày bán`, sáu mốc giá trị,
năm mốc ngày và các đường lưới để xác định vị trí. Mỗi ngày có một điểm tương tác; rê chuột lên điểm
sẽ hiện ngày đầy đủ cùng số sản phẩm chính xác. Trên màn hình hẹp, khu vực biểu đồ cho phép cuộn ngang
để nhãn trục không bị ép chồng lên nhau.

## Phân tích đa cấp ngày, tuần và tháng

Người dùng có thể chuyển giữa `Theo ngày`, `Theo tuần`, `Theo tháng`. Chế độ tuần cộng doanh số từ
thứ Hai đến Chủ Nhật; chế độ tháng cộng toàn bộ ngày trong tháng. Mỗi chế độ hiển thị tổng nhu cầu,
trung bình theo kỳ, kỳ có nhu cầu cao nhất và phần trăm thay đổi của kỳ cuối so với kỳ liền trước.
Biểu đồ, tooltip và nhãn trục thay đổi theo mức tổng hợp được chọn.

Khối `Sản phẩm bán chạy theo tháng` so sánh cả 50 mã sản phẩm và hiển thị top 5 theo tổng doanh số,
kèm trung bình/ngày và đỉnh/ngày. Người dùng chọn một trong 12 tháng năm 2017. Đây là phân tích mô tả
dữ liệu đã bán, hữu ích để quan sát xu hướng, mùa vụ và nhu cầu tương đối; không phải dự báo tương lai,
không chứng minh quan hệ nhân quả và không tự quyết định lượng nhập hàng.

## So sánh tổng dự báo với cùng kỳ tuần trước

Khối tổng kết dự báo bảy ngày hiển thị song song tổng số sản phẩm dự kiến bán và tổng số đã bán trong
bảy ngày tương ứng của tuần trước (`Weekly Naive`). Chênh lệch được trình bày cả bằng số sản phẩm và
phần trăm: `(tổng dự báo - tổng tuần trước) / tổng tuần trước × 100`. Giao diện ghi rõ nhu cầu tăng,
giảm hoặc không đổi. Nếu tổng tuần trước bằng 0, giao diện không tính phần trăm để tránh chia cho 0 và
hiển thị lý do; số chênh lệch tuyệt đối vẫn được giữ.

## Màn hình nhập dữ liệu

Dashboard hiện có bảy màn hình, bổ sung `Nhập dữ liệu`. Người dùng chọn một file `.csv`, thấy rõ bốn
cột bắt buộc và gửi multipart tới API. Sau khi tiếp nhận, giao diện tự hỏi trạng thái mỗi hai giây và
trình bày `Đã tiếp nhận`, `Đang xử lý bằng Spark`, `Hoàn thành` hoặc `Thất bại`. Nếu thất bại, giao diện
hiện lỗi và nút thử lại; khi hoàn thành, bảng pipeline được làm mới và dữ liệu PostgreSQL đã sẵn sàng.

`npm test` và `npm run build` đều PASS; image frontend đã recreate và HTTP `localhost:3000` trả 200.
Công cụ Chrome và trình duyệt tích hợp đều báo không khả dụng trong phiên nên chưa có visual,
responsive hoặc keyboard QA thực tế cho màn hình mới.

### Theo dõi tiến độ xử lý chi tiết

Màn hình nhập dữ liệu hiện trình bày timeline bảy công đoạn từ upload đến làm mới Dashboard. Phía trên
có phần trăm tổng, thời gian đã chạy, tốc độ trung bình theo bản ghi/giây, số bản ghi đã xử lý/tổng số
bản ghi và thanh tiến độ có thuộc tính accessibility `progressbar`. Từng công đoạn có trạng thái đang
chờ, đang thực hiện, hoàn thành hoặc thất bại; giao diện tiếp tục polling hai giây một lần.

Phần trăm được tính từ các stage backend đã ghi nhận và tỷ lệ bản ghi thật trong stage hiện hành, không
chạy giả theo đồng hồ. Với run đã hoàn thành, tốc độ dùng `goldRows / durationMillis`. Timeline chi tiết
hiện gắn với file vừa upload trong phiên; danh sách lịch sử chưa có thao tác mở lại chi tiết một run cũ.
