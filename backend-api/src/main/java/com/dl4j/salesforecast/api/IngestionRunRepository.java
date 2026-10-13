package com.dl4j.salesforecast.api;

import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class IngestionRunRepository {
    private final JdbcTemplate jdbc;
    IngestionRunRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Map<String,Object> find(long id) {
        Map<String,Object> run = jdbc.queryForMap(selectRun() + " WHERE id=?", id);
        return withStages(run);
    }

    Map<String,Object> findByChecksum(String checksum) {
        List<Map<String,Object>> rows = jdbc.queryForList(selectRun() + " WHERE checksum_sha256=?", checksum);
        return rows.isEmpty() ? null : withStages(rows.get(0));
    }

    long create(String checksum, String fileName, String sourceUri, long count) {
        long id = jdbc.queryForObject("INSERT INTO data_ingestion_runs(idempotency_key,checksum_sha256,original_filename,status,source_uri,record_count,current_stage,processed_rows) "
                + "VALUES (?, ?, ?, 'ACCEPTED', ?, ?, 'STORE_BRONZE', ?) RETURNING id", Long.class,
                checksum, checksum, fileName, sourceUri, count, count);
        completeInitialStage(id, "UPLOAD", count);
        completeInitialStage(id, "VALIDATE", count);
        completeInitialStage(id, "STORE_BRONZE", count);
        return id;
    }

    void running(long id) {
        jdbc.update("UPDATE data_ingestion_runs SET status='RUNNING',current_stage='SPARK_READ',processed_rows=0,attempt_count=attempt_count+1,error_message=NULL,finished_at=NULL,updated_at=now() WHERE id=?", id);
        jdbc.update("INSERT INTO pipeline_runs(ingestion_run_id,status,raw_rows) SELECT id,'RUNNING',record_count FROM data_ingestion_runs WHERE id=?", id);
        stage(id, "SPARK_READ", "RUNNING", 0, recordCount(id), null);
    }

    void progress(long id, SparkSubmitter.Progress progress) {
        String status = progress.totalRows() > 0 && progress.completedRows() >= progress.totalRows()
                ? "COMPLETED" : "RUNNING";
        stage(id, progress.stage(), status, progress.completedRows(), progress.totalRows(), null);
        jdbc.update("UPDATE data_ingestion_runs SET current_stage=?,processed_rows=?,updated_at=now() WHERE id=?",
                progress.stage(), progress.completedRows(), id);
    }

    void publishing(long id) {
        stage(id, "PUBLISH_POSTGRESQL", "RUNNING", 0, recordCount(id), null);
        jdbc.update("UPDATE data_ingestion_runs SET current_stage='PUBLISH_POSTGRESQL',processed_rows=0,updated_at=now() WHERE id=?", id);
    }

    @Transactional
    void publish(long id, List<SalesUploadValidator.SalesRow> rows, long durationMs) {
        for (SalesUploadValidator.SalesRow row : rows) {
            jdbc.update("INSERT INTO stores(id,name) VALUES (?,?) ON CONFLICT (id) DO NOTHING", row.store(), "Store " + row.store());
            jdbc.update("INSERT INTO items(id,store_id,name) VALUES (?,?,?) ON CONFLICT (id) DO NOTHING", row.item(), row.store(), "Item " + row.item());
        }
        jdbc.batchUpdate("INSERT INTO sales_records(store_id,item_id,sales_date,sales) VALUES (?,?,?,?) "
                + "ON CONFLICT (store_id,item_id,sales_date) DO UPDATE SET sales=EXCLUDED.sales",
                rows, 1000, (ps, row) -> { ps.setInt(1, row.store()); ps.setInt(2, row.item()); ps.setDate(3, Date.valueOf(row.date())); ps.setInt(4, row.sales()); });
        stage(id, "PUBLISH_POSTGRESQL", "COMPLETED", rows.size(), rows.size(), null);
        stage(id, "REFRESH_DASHBOARD", "COMPLETED", rows.size(), rows.size(), null);
        jdbc.update("UPDATE data_ingestion_runs SET status='COMPLETED',current_stage='REFRESH_DASHBOARD',processed_rows=?,finished_at=now(),updated_at=now() WHERE id=?", rows.size(), id);
        jdbc.update("UPDATE pipeline_runs SET status='COMPLETED',gold_rows=?,duration_ms=? WHERE id=(SELECT max(id) FROM pipeline_runs WHERE ingestion_run_id=?)",
                rows.size(), durationMs, id);
    }

    void failed(long id, String error, long durationMs) {
        String message = error == null ? "Spark xử lý thất bại" : error;
        if (message.length() > 4000) message = message.substring(message.length() - 4000);
        jdbc.update("UPDATE data_ingestion_runs SET status='FAILED',error_message=?,finished_at=now(),updated_at=now() WHERE id=?", message, id);
        jdbc.update("UPDATE pipeline_runs SET status='FAILED',error_message=?,duration_ms=? WHERE id=(SELECT max(id) FROM pipeline_runs WHERE ingestion_run_id=?)",
                message, durationMs, id);
        jdbc.update("UPDATE ingestion_stage_events SET status='FAILED',finished_at=now(),message=? WHERE ingestion_run_id=? AND attempt=? AND status='RUNNING'",
                message, id, attempt(id));
    }

    private String selectRun() {
        return "SELECT id,status,source_uri AS \"sourceUri\",checksum_sha256 AS checksum,"
                + "original_filename AS \"fileName\",record_count AS \"recordCount\",attempt_count AS attempts,"
                + "current_stage AS \"currentStage\",processed_rows AS \"processedRows\","
                + "error_message AS error,started_at AS \"startedAt\",finished_at AS \"finishedAt\",updated_at AS \"updatedAt\" FROM data_ingestion_runs";
    }

    private Map<String,Object> withStages(Map<String,Object> source) {
        Map<String,Object> run = new LinkedHashMap<>(source);
        int attempt = ((Number) run.getOrDefault("attempts", 0)).intValue();
        run.put("stages", jdbc.queryForList("SELECT stage,status,completed_rows AS \"completedRows\",total_rows AS \"totalRows\",started_at AS \"startedAt\",finished_at AS \"finishedAt\",message FROM ingestion_stage_events WHERE ingestion_run_id=? AND (attempt=0 OR attempt=?) ORDER BY id", run.get("id"), attempt));
        List<Map<String,Object>> pipeline = jdbc.queryForList("SELECT duration_ms AS \"durationMillis\",raw_rows AS \"rawRows\",gold_rows AS \"goldRows\" FROM pipeline_runs WHERE ingestion_run_id=? ORDER BY id DESC LIMIT 1", run.get("id"));
        if (!pipeline.isEmpty()) run.putAll(pipeline.get(0));
        return run;
    }

    private void completeInitialStage(long id, String name, long rows) {
        jdbc.update("INSERT INTO ingestion_stage_events(ingestion_run_id,attempt,stage,status,completed_rows,total_rows,finished_at) VALUES (?,0,?,'COMPLETED',?,?,now())", id, name, rows, rows);
    }

    private void stage(long id, String name, String status, long completed, long total, String message) {
        int attempt = attempt(id);
        jdbc.update("INSERT INTO ingestion_stage_events(ingestion_run_id,attempt,stage,status,completed_rows,total_rows,finished_at,message) VALUES (?,?,?,?,?,?,CASE WHEN ?='COMPLETED' THEN now() ELSE NULL END,?) ON CONFLICT (ingestion_run_id,attempt,stage) DO UPDATE SET status=EXCLUDED.status,completed_rows=EXCLUDED.completed_rows,total_rows=EXCLUDED.total_rows,finished_at=CASE WHEN EXCLUDED.status='COMPLETED' THEN now() ELSE ingestion_stage_events.finished_at END,message=EXCLUDED.message",
                id, attempt, name, status, completed, total, status, message);
    }

    private int attempt(long id) {
        return jdbc.queryForObject("SELECT attempt_count FROM data_ingestion_runs WHERE id=?", Integer.class, id);
    }

    private long recordCount(long id) {
        return jdbc.queryForObject("SELECT record_count FROM data_ingestion_runs WHERE id=?", Long.class, id);
    }
}
