# Sơ đồ kiến trúc

Các sơ đồ phản ánh thành phần hiện có trong source và Compose.

## Docker và request flow

```mermaid
flowchart LR
    Browser --> Nginx[Frontend / Nginx]
    Nginx -->|/api| API[Spring Boot API]
    API --> PostgreSQL[(PostgreSQL)]
    API --> Forecast[DL4J Forecast Service]
    API --> MinIO[(MinIO)]
    API --> Runner[Spark job runner]
    Runner --> Master[Spark Master]
    Master --> W1[Spark Worker 1]
    Master --> W2[Spark Worker 2]
```

## Ingestion và Data Lake

```mermaid
flowchart LR
    CSV --> Validate[Validate + checksum]
    Validate --> Bronze[MinIO Bronze CSV]
    Bronze --> Spark[Spark quality + transform]
    Spark --> Silver[Silver Parquet]
    Spark --> Gold[Gold Parquet + statistics]
    Gold --> Publish[Transactional publish]
    Publish --> PostgreSQL[(PostgreSQL)]
    PostgreSQL --> Dashboard
```

## Training, checkpoint và deployment

```mermaid
flowchart LR
    Train[2013-2015] --> Scale[Per-item train-only scaler]
    Scale --> LSTM[GlobalResidualAR]
    Validation[Validation 2016] --> Select[Select seed/epoch]
    LSTM --> Checkpoints[Per-seed checkpoints]
    Checkpoints --> Select
    Select --> Package[Deployment package]
    Package --> CPU[Forecast Service CPU]
    Test[Test 2017] --> Eval[Evaluation-only]
    Package --> Eval
```

## Inference H1-H7

```mermaid
sequenceDiagram
    participant UI as Dashboard
    participant API as Spring Boot API
    participant FS as Forecast Service
    participant M as GlobalResidualAR
    UI->>API: item/origin hoặc batch 50 items
    API->>FS: forecast request
    FS->>M: 30 ngày, 56 channels
    M-->>FS: residual H1-H7
    FS-->>API: reconstructed sales + Weekly Naive
    API-->>UI: 7 ngày dự báo
```

Artifact active vẫn được mount từ đường dẫn lịch sử. Manifest compatibility nằm tại
`artifacts/manifests/current-model.json`.
