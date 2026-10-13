# Tài liệu quá trình thực hiện đồ án

Đồ án: **Dự báo nhu cầu bán hàng bằng DeepLearning4J**.

Đọc theo thứ tự:

1. [Audit repository Big Data](00-repository-audit.md): bằng chứng nền tảng, artifact và blocker trước tích hợp.
2. [Kiến trúc Big Data](01-architecture.md): topology Docker/Spark/MinIO/PostgreSQL và ranh giới từng phase.
3. [Data Lake và pipeline](02-data-pipeline.md): Bronze ingest, artifact model, persistence và quality gate.
4. [Spark ETL](03-spark-etl.md): Silver/Gold Parquet, quality gate, count và runtime parity.
5. [DL4J Forecast Service](04-model-serving.md): CPU checkpoint serving, HTTP contract và forecast parity.
6. [PostgreSQL và Spring Boot API](05-api.md): migration, endpoint, timeout, idempotency và bằng chứng runtime.
7. [React Dashboard](06-dashboard.md): sáu màn hình, API proxy và giới hạn browser QA.
8. [Checklist triển khai Big Data](11-implementation-checklist.md): trạng thái từng giai đoạn và 12 tiêu chí nghiệm thu.
9. [Lịch sử triển khai](implementation-history.md): từng bước, nhu cầu, thay đổi và kết quả.
10. [Trạng thái hiện tại](current-state.md): pipeline, giao diện các lớp, số liệu và giới hạn.
11. [Nhật ký yêu cầu](change-log.md): cập nhật sau mỗi yêu cầu, bằng chứng kiểm tra và tên commit đề xuất.
12. [Thí nghiệm Residual AR](residual-experiment.md): thiết kế, protocol, artifact và kết quả full 180 fits.
13. [Thí nghiệm ResidualAR Huber](residual-huber-experiment.md): full 120 fits đã hoàn tất; Huber gần bằng MSE, chưa vượt Weekly.
14. [Một store, global model nhiều items](single-store-global-experiment.md): benchmark GPU50items, checkpoint/deployment package, preflight, script GPU và lệnh full 3 seed; full 3 seed chưa chạy.
15. [Hướng dẫn training trên máy GPU mới](gpu-training-guide.md): prerequisites, preflight, một lệnh full, artifact và audit.

Evaluation-only cho checkpoint GlobalResidualAR được gọi bằng
`scripts/Invoke-GlobalTestEvaluation.ps1 -SourceRun <full-run>`. Runner tạo thư mục
`test-evaluation/` riêng, không train, không thay deployment selection và không overwrite kết quả cũ.

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
