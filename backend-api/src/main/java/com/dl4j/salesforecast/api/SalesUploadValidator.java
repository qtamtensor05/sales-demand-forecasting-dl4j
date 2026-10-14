package com.dl4j.salesforecast.api;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
class SalesUploadValidator {
    private static final List<String> REQUIRED_HEADER = List.of("date", "store", "item", "sales");

    ValidatedCsv validate(byte[] content) {
        if (content == null || content.length == 0) throw new IllegalArgumentException("File CSV không được rỗng");
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setTrim(true).build().parse(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            if (!new ArrayList<>(parser.getHeaderMap().keySet()).equals(REQUIRED_HEADER)) {
                throw new IllegalArgumentException("CSV phải có đúng các cột: date, store, item, sales");
            }
            List<SalesRow> rows = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            for (CSVRecord record : parser) {
                long line = record.getRecordNumber() + 1;
                if (record.size() != 4) throw invalid(line, "số cột không hợp lệ");
                LocalDate date;
                int store;
                int item;
                int sales;
                try {
                    date = LocalDate.parse(record.get("date"));
                    store = Integer.parseInt(record.get("store"));
                    item = Integer.parseInt(record.get("item"));
                    sales = Integer.parseInt(record.get("sales"));
                } catch (DateTimeParseException | NumberFormatException failure) {
                    throw invalid(line, "ngày hoặc giá trị số không hợp lệ");
                }
                if (store != 1) throw invalid(line, "phiên bản hiện tại chỉ hỗ trợ store = 1");
                if (item < 1 || item > 50) throw invalid(line, "item phải nằm trong khoảng 1..50");
                if (sales < 0) throw invalid(line, "sales không được âm");
                String key = date + "|" + store + "|" + item;
                if (!keys.add(key)) throw invalid(line, "trùng date, store, item trong file");
                rows.add(new SalesRow(date, store, item, sales));
            }
            if (rows.isEmpty()) throw new IllegalArgumentException("CSV phải có ít nhất một bản ghi dữ liệu");
            return new ValidatedCsv(List.copyOf(rows));
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("Không thể đọc file CSV: " + failure.getMessage(), failure);
        }
    }

    private IllegalArgumentException invalid(long line, String message) {
        return new IllegalArgumentException("Dòng " + line + ": " + message);
    }

    static final class ValidatedCsv {
        private final List<SalesRow> rows;
        ValidatedCsv(List<SalesRow> rows) { this.rows = rows; }
        List<SalesRow> rows() { return rows; }
    }

    static final class SalesRow {
        private final LocalDate date; private final int store; private final int item; private final int sales;
        SalesRow(LocalDate date, int store, int item, int sales) {
            this.date = date; this.store = store; this.item = item; this.sales = sales;
        }
        LocalDate date() { return date; } int store() { return store; } int item() { return item; } int sales() { return sales; }
    }
}
