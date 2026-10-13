# 04. DL4J Forecast Service

Forecast Service hiện chạy Java 11 và ND4J CPU trong Docker. Service nạp đúng deployment package seed
123, không huấn luyện lại và không sửa checkpoint/scaler.

## Endpoint

- `GET /health`: trạng thái, CPU backend, seed và SHA-256 model;
- `GET /metadata`: metadata checkpoint và runtime backend;
- `POST /forecast`: nhận `itemId` 1-50 và `originDate` ISO, trả H1-H7 gồm GlobalResidualAR,
  Weekly Naive, actual (khi có trong dataset), target date và inference time.

Ví dụ:

```powershell
$body = @{ itemId = 25; originDate = '2017-06-30' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8083/forecast `
  -ContentType 'application/json' -Body $body
```

## Bằng chứng runtime

- backend: `org.nd4j.linalg.cpu.nativecpu.CpuBackend` trên Linux;
- checkpoint SHA-256:
  `f9d2dee2d3fcfb31ed61e08cd7b9b5f92d8ca9a7f1b0a95e5447afa477c7a482`;
- health `UP`, model seed 123 đã load;
- item 25/origin 2017-06-30 trả đủ H1-H7; request item 51 trả HTTP 400;
- parity 21 forecast points tại ba item/origin so với artifact GPU seed 123: Weekly Naive sai khác 0,
  model sai khác lớn nhất `5,06937779931604e-5`, đạt tolerance CPU/GPU `1e-3`;
- một request đo được khoảng 428,5 ms; đây là số đo smoke đơn lẻ, không phải benchmark.

## Build CPU package

```powershell
mvn -B -Pcpu-service -DskipTests package
docker compose build forecast-service
docker compose up -d --no-deps forecast-service
```

Giới hạn hiện tại: profile `cpu-service` dùng `nd4j-native-platform`, làm fat JAR khoảng 1,45 GB vì chứa
native binaries đa nền tảng. Service đã chạy đúng nhưng artifact cần được thu gọn Linux x86_64 trước
khi xem là image production tối ưu. API nghiệp vụ Spring Boot sẽ gọi service này ở giai đoạn kế tiếp.
