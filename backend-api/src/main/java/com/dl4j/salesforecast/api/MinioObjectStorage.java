package com.dl4j.salesforecast.api;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class MinioObjectStorage implements ObjectStorage {
    private final MinioClient client;
    private final String bucket;

    MinioObjectStorage(@Value("${minio.endpoint}") String endpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${ingestion.bucket:bronze}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
    }

    @Override public void put(String objectName, byte[] content) {
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectName)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType("text/csv").build());
        } catch (Exception failure) {
            throw new IllegalStateException("Không thể lưu file vào MinIO", failure);
        }
    }

    @Override public byte[] get(String objectName) {
        try (var input = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectName).build())) {
            return input.readAllBytes();
        } catch (Exception failure) {
            throw new IllegalStateException("Không thể đọc file từ MinIO", failure);
        }
    }
}
