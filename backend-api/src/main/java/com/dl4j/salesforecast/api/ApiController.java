package com.dl4j.salesforecast.api;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class ApiController {
    private final JdbcTemplate jdbc;
    private final ForecastGateway forecasts;

    ApiController(JdbcTemplate jdbc, ForecastGateway forecasts) {
        this.jdbc = jdbc; this.forecasts = forecasts;
    }

    @GetMapping("/health") Map<String, Object> health() {
        Integer database = jdbc.queryForObject("SELECT 1", Integer.class);
        return Map.of("status", "UP", "service", "backend-api", "database", database != null ? "UP" : "DOWN");
    }

    @GetMapping("/stores") List<Map<String, Object>> stores() {
        return jdbc.queryForList("SELECT id,name FROM stores ORDER BY id");
    }

    @GetMapping("/items") List<Map<String, Object>> items() {
        return jdbc.queryForList("SELECT id,store_id AS \"storeId\",name FROM items ORDER BY id");
    }

    @GetMapping("/items/{itemId}/sales") Map<String, Object> sales(@PathVariable int itemId,
            @RequestParam(required=false) String from, @RequestParam(required=false) String to,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="100") int size) {
        if (itemId < 1 || itemId > 50) throw new IllegalArgumentException("itemId must be between 1 and 50");
        if (page < 0 || size < 1 || size > 1000) throw new IllegalArgumentException("page >= 0 and size must be 1..1000");
        LocalDate start = from == null ? LocalDate.of(2013,1,1) : LocalDate.parse(from);
        LocalDate end = to == null ? LocalDate.of(2017,12,31) : LocalDate.parse(to);
        if (end.isBefore(start)) throw new IllegalArgumentException("to must not be before from");
        Long total = jdbc.queryForObject("SELECT count(*) FROM sales_records WHERE item_id=? AND sales_date BETWEEN ? AND ?", Long.class,
                itemId, Date.valueOf(start), Date.valueOf(end));
        List<Map<String,Object>> content = jdbc.queryForList("SELECT sales_date AS date,sales FROM sales_records WHERE item_id=? AND sales_date BETWEEN ? AND ? ORDER BY sales_date LIMIT ? OFFSET ?",
                itemId, Date.valueOf(start), Date.valueOf(end), size, page * size);
        Map<String,Object> result = new LinkedHashMap<>(); result.put("content", content); result.put("page", page); result.put("size", size); result.put("totalElements", total);
        return result;
    }

    @GetMapping("/analytics/monthly-demand") List<Map<String, Object>> monthlyDemand(
            @RequestParam(defaultValue="2017") int year,
            @RequestParam(defaultValue="5") int limit) {
        if (year < 2013 || year > 2017) throw new IllegalArgumentException("year must be between 2013 and 2017");
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("limit must be between 1 and 50");
        String sql = "WITH monthly AS ("
                + " SELECT date_trunc('month', sales_date) AS month, item_id,"
                + " SUM(sales) AS total_sales, ROUND(AVG(sales)::numeric,1) AS average_daily_sales, MAX(sales) AS peak_daily_sales"
                + " FROM sales_records WHERE EXTRACT(YEAR FROM sales_date)=? GROUP BY 1,2),"
                + " ranked AS (SELECT *, ROW_NUMBER() OVER (PARTITION BY month ORDER BY total_sales DESC, item_id) AS demand_rank FROM monthly)"
                + " SELECT to_char(month,'YYYY-MM') AS month, item_id AS \"itemId\", total_sales AS \"totalSales\","
                + " average_daily_sales AS \"averageDailySales\", peak_daily_sales AS \"peakDailySales\", demand_rank AS rank"
                + " FROM ranked WHERE demand_rank<=? ORDER BY month,demand_rank";
        return jdbc.queryForList(sql, year, limit);
    }

    @PostMapping("/forecasts") @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> createForecast(@RequestBody Map<String,Object> body) {
        Object raw = body.get("itemId");
        Integer itemId = raw instanceof Number ? ((Number) raw).intValue() : null;
        return forecasts.create(itemId, body.get("originDate") == null ? null : body.get("originDate").toString());
    }

    @GetMapping("/forecasts/{id}") Map<String,Object> forecast(@PathVariable UUID id) { return forecasts.find(id); }

    @GetMapping("/models") List<Map<String,Object>> models() {
        return jdbc.queryForList("SELECT id,name,seed,checkpoint_sha256 AS \"checkpointSha256\",status,created_at AS \"createdAt\" FROM model_versions ORDER BY id DESC");
    }

    @GetMapping("/metrics") List<Map<String,Object>> metrics() {
        return jdbc.queryForList("SELECT id,model_version_id AS \"modelVersionId\",dataset_split AS \"datasetSplit\",metric_name AS \"metricName\",metric_value AS \"metricValue\",scope FROM evaluation_metrics ORDER BY id DESC");
    }

    @GetMapping("/pipelines") List<Map<String,Object>> pipelines() {
        return jdbc.queryForList("SELECT id,ingestion_run_id AS \"ingestionRunId\",status,raw_rows AS \"rawRows\",gold_rows AS \"goldRows\",duration_ms AS \"durationMillis\",created_at AS \"createdAt\" FROM pipeline_runs ORDER BY id DESC");
    }

    @GetMapping("/benchmarks") List<Map<String,Object>> benchmarks() {
        return jdbc.queryForList("SELECT id,worker_count AS \"workerCount\",input_rows AS \"inputRows\",duration_ms AS \"durationMillis\",throughput_rows_second AS \"throughputRowsSecond\",measured_at AS \"measuredAt\" FROM benchmark_runs ORDER BY id DESC");
    }

    @PostMapping("/pipelines/ingest") @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String,Object> ingest(@RequestHeader(value="Idempotency-Key", required=false) String key) {
        if (key == null || key.isBlank() || key.length() > 100) throw new IllegalArgumentException("Idempotency-Key header is required (1..100 chars)");
        jdbc.update("INSERT INTO data_ingestion_runs(idempotency_key,status,source_uri) VALUES (?,'ACCEPTED','s3a://bronze/store-1/train.csv') ON CONFLICT (idempotency_key) DO NOTHING", key);
        return jdbc.queryForMap("SELECT id,idempotency_key AS \"idempotencyKey\",status,source_uri AS \"sourceUri\",started_at AS \"startedAt\" FROM data_ingestion_runs WHERE idempotency_key=?", key);
    }
}
