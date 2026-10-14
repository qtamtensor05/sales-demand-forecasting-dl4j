# Hợp đồng dữ liệu

CSV đầu vào bắt buộc có đúng header:

```csv
date,store,item,sales
```

- `date`: ISO `yyyy-MM-dd`;
- `store`, `item`: số nguyên dương;
- `sales`: số nguyên không âm;
- khóa nghiệp vụ: `date,store,item`;
- dữ liệu global model hiện tại: Store 1, item 1–50.

Training giữ split thời gian 2013–2015 / 2016 / 2017 và scaler chỉ fit trên train. Upload runtime
không tự retrain hoặc thay checkpoint.
