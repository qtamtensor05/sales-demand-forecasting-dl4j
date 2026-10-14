package com.dl4j.salesforecast.api;

import java.security.MessageDigest;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
class IngestionService {
    private final SalesUploadValidator validator;
    private final ObjectStorage storage;
    private final IngestionRunRepository runs;
    private final IngestionWorker worker;

    IngestionService(SalesUploadValidator validator, ObjectStorage storage,
            IngestionRunRepository runs, IngestionWorker worker) {
        this.validator = validator; this.storage = storage; this.runs = runs; this.worker = worker;
    }

    Map<String,Object> accept(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Hãy chọn một file CSV không rỗng");
        String original = safeName(file.getOriginalFilename());
        if (!original.toLowerCase().endsWith(".csv")) throw new IllegalArgumentException("Chỉ chấp nhận file .csv");
        try {
            byte[] content = file.getBytes();
            SalesUploadValidator.ValidatedCsv csv = validator.validate(content);
            String checksum = sha256(content);
            Map<String,Object> existing = runs.findByChecksum(checksum);
            if (existing != null) return duplicate(existing);
            String objectName = "uploads/" + checksum + "/" + original;
            storage.put(objectName, content);
            long id;
            try {
                id = runs.create(checksum, original, "s3a://bronze/" + objectName, csv.rows().size());
            } catch (DuplicateKeyException race) {
                return duplicate(runs.findByChecksum(checksum));
            }
            worker.process(id);
            return runs.find(id);
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Không thể tiếp nhận file CSV", failure);
        }
    }

    Map<String,Object> find(long id) { return runs.find(id); }

    Map<String,Object> retry(long id) {
        Map<String,Object> run = runs.find(id);
        if (!"FAILED".equals(run.get("status"))) throw new IllegalArgumentException("Chỉ có thể thử lại một lần xử lý đã thất bại");
        worker.process(id);
        Map<String,Object> response = runs.find(id);
        response.put("retryScheduled", true);
        return response;
    }

    private Map<String,Object> duplicate(Map<String,Object> existing) {
        existing.put("duplicate", true);
        existing.put("message", "File này đã được tiếp nhận trước đó; hệ thống không xử lý lại");
        return existing;
    }

    private String safeName(String value) {
        String name = value == null ? "sales.csv" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isBlank() || name.length() > 200) throw new IllegalArgumentException("Tên file không hợp lệ");
        return name;
    }

    private String sha256(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
        return hex.toString();
    }
}
