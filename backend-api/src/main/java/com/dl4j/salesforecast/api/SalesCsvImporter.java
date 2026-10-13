package com.dl4j.salesforecast.api;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class SalesCsvImporter implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(SalesCsvImporter.class);
    private final JdbcTemplate jdbc;
    private final Path csv;

    SalesCsvImporter(JdbcTemplate jdbc, @Value("${sales.csv-path}") String csv) {
        this.jdbc = jdbc;
        this.csv = Path.of(csv);
    }

    @Override public void run(ApplicationArguments args) throws Exception {
        Long count = jdbc.queryForObject("SELECT count(*) FROM sales_records", Long.class);
        if (count != null && count > 0) return;
        if (!Files.isRegularFile(csv)) {
            LOG.warn("Sales CSV not found at {}; analytics endpoint will be empty", csv);
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(csv)) {
            String header = reader.readLine();
            if (!"date,store,item,sales".equals(header)) throw new IllegalStateException("Unexpected CSV header");
            List<Object[]> batch = new ArrayList<>(1000);
            String line;
            long imported = 0;
            while ((line = reader.readLine()) != null) {
                String[] value = line.split(",", -1);
                if (value.length != 4 || Integer.parseInt(value[1]) != 1) continue;
                batch.add(new Object[] {1, Integer.parseInt(value[2]), Date.valueOf(LocalDate.parse(value[0])), Integer.parseInt(value[3])});
                if (batch.size() == 1000) { imported += insert(batch); batch.clear(); }
            }
            imported += insert(batch);
            LOG.info("Imported {} Store 1 sales records from {}", imported, csv);
        }
    }

    private int insert(List<Object[]> batch) {
        if (batch.isEmpty()) return 0;
        jdbc.batchUpdate("INSERT INTO sales_records(store_id,item_id,sales_date,sales) VALUES (?,?,?,?) ON CONFLICT DO NOTHING", batch);
        return batch.size();
    }
}
