# Phương pháp benchmark

Benchmark mới ghi vào `output/benchmarks/<benchmark-name>/<run-id>/`. Không đưa số liệu ước lượng
hoặc synthetic vào bảng kết quả runtime thực tế.

Global runtime benchmark:

```powershell
.\project.ps1 benchmark
```

So sánh Spark 1/2 worker phải giữ cùng input, transformation, resource policy, warm-up và số lần lặp;
lưu command, thời gian, row count, cấu hình worker và lỗi trong manifest. Benchmark Spark này hiện
chưa có bằng chứng hoàn tất, vì vậy không có số liệu được tạo trong tài liệu.
