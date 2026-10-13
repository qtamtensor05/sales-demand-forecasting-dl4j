# 03. Spark ETL

## Luồng đã triển khai

`Store1EtlJob` đọc trực tiếp `s3a://bronze/store-1/train.csv` để driver và hai executor cùng truy cập
một nguồn dữ liệu. Job thực hiện:

1. parse schema CSV theo chế độ fail-fast và lọc Store 1;
2. kiểm tra missing, duplicate `store-item-date`, đủ 50 item và đủ 1.826 ngày/item;
3. gắn split thời gian Train 2013-2015, Validation 2016 và Test 2017;
4. fit min/max riêng từng item chỉ trên Train;
5. tạo sáu feature động causal và 50 kênh item identity;
6. ghi Silver và Gold dưới dạng Snappy Parquet vào MinIO;
7. ghi thống kê chạy thật vào `benchmarks/etl/store-1-latest`.

Gold giữ `item`, `date`, `split`, sales gốc, train min/max, sales đã scale, `sales_lag_7`,
`rolling_mean_7`, `rolling_mean_14`, sin/cos thứ trong tuần, `feature_ready` và `item_1` đến `item_50`.
Rolling 7/14 bao gồm ngày feature hiện tại, đúng với `SalesPreprocessor`; không đọc tương lai.

## Build và chạy

```powershell
mvn -B -f spark-etl/pom.xml -Pspark-runtime package
docker compose exec -T spark-job-runner /opt/spark/bin/spark-submit `
  --master spark://spark-master:7077 `
  --class com.dl4j.salesforecast.etl.Store1EtlJob `
  --conf spark.executor.instances=2 `
  --conf spark.executor.cores=1 `
  --conf spark.executor.memory=512m `
  /workspace/spark-etl/target/spark-etl.jar
```

## Kết quả thực tế ngày 2026-10-13

- raw/Gold: 91.300 dòng, 50 item;
- Train/Validation/Test: 54.750 / 18.300 / 18.250;
- training windows/validation origins/test origins: 52.300 / 18.000 / 17.950;
- hai executor chạy trên hai Spark worker;
- lượt cuối hoàn tất trong 64.695 ms theo thống kê job;
- runtime parity PASS trên 12 hàng thuộc item 1/25/50 và bốn mốc thời gian, sáu feature động cùng 50
  identity channels, tolerance `1e-9`.

Lần chạy đầu đọc file mount local đã fail vì executor không có đường dẫn của driver. Input sau đó được
chuyển sang Bronze S3A dùng chung. Cảnh báo FileOutputCommitter của S3A vẫn còn; job hiện phù hợp MVP
nhưng cần committer an toàn hơn trước khi xem là production multi-writer.

Benchmark so sánh lặp lại 1 Worker/2 Workers chưa thực hiện; không dùng thời gian ETL đơn lẻ ở trên để
kết luận Spark nhanh hơn.
