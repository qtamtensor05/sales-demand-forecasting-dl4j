# Yêu cầu hệ thống

- Windows 11, PowerShell 7 và Docker Desktop Linux containers qua WSL2.
- JDK 11; không dùng JDK mới hơn để thay đổi bytecode đích.
- Maven 3.6.3 trở lên.
- `data/train.csv` theo hợp đồng tại [data contracts](../03-data/data-contracts.md).
- Deployment package đầy đủ gồm model, metadata, scaler, item mapping, feature schema và selection.

GPU NVIDIA và CUDA Toolkit 11.6 chỉ cần cho training. Inference trong Docker dùng profile
`cpu-service` và ND4J CPU.
